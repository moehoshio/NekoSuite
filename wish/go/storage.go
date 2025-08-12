package wish

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
)

// UserData 用户祈愿数据结构
type UserData struct {
	// 祈愿次数统计 (池名 -> 次数，用于保底计算)
	WishCounts map[string]int `yaml:"wish_counts"`

	// 每日祈愿记录 (池名 -> 日期) - 已废弃，改用 LimitModeRecords
	DailyWish map[string]string `yaml:"daily_wish,omitempty"`

	// 限制模式记录 (池名 -> 周期使用记录)
	LimitModeRecords map[string]LimitModeRecord `yaml:"limit_mode_records"`

	// 保底统计 (池名 -> 当前保底计数，已废弃，改用 WishCounts)
	GuaranteeCounts map[string]int `yaml:"guarantee_counts,omitempty"`

	// 祈愿统计 (累计数据，不会因保底重置)
	WishStats struct {
		// 累计祈愿次数 (池名 -> 总次数)
		TotalWishes map[string]int `yaml:"total_wishes"`
		// 获得保底次数 (池名 -> 保底次数)
		GuaranteeCount map[string]int `yaml:"guarantee_count"`
	} `yaml:"wish_stats"`

	// 祈愿券数量 (券类型 -> 数量)
	WishTickets map[string]int `yaml:"wish_tickets"`

	// 祈愿历史记录 (可选，用于统计分析)
	WishHistory []WishRecord `yaml:"wish_history,omitempty"`

	// 用户创建时间和最后更新时间
	CreatedAt string `yaml:"created_at"`
	UpdatedAt string `yaml:"updated_at"`
}

// LimitModeRecord 限制模式记录
type LimitModeRecord struct {
	CurrentPeriodStart time.Time `yaml:"current_period_start"` // 当前周期开始时间
	CurrentPeriodEnd   time.Time `yaml:"current_period_end"`   // 当前周期结束时间
	UsedCount          int       `yaml:"used_count"`           // 当前周期已使用次数
	LastRefreshTime    time.Time `yaml:"last_refresh_time"`    // 上次刷新时间
}

// WishRecord 单次祈愿记录
type WishRecord struct {
	Pool        string         `yaml:"pool"`                   // 祈愿池
	Count       int            `yaml:"count"`                  // 祈愿次数
	Items       []string       `yaml:"items"`                  // 获得物品
	Cost        int            `yaml:"cost"`                   // 花费
	TicketsUsed map[string]int `yaml:"tickets_used,omitempty"` // 使用的祈愿券详情
	Timestamp   time.Time      `yaml:"timestamp"`              // 祈愿时间
}

// Storage 存储接口
type Storage interface {
	InitUserDir(user string, dir string) error
	GetUserData(user string) (*UserData, error)
	SaveUserData(user string, data *UserData) error
	GetWishCount(user string, poolName string) (int, error)
	UpdateWishCount(user string, poolName string, count int) error
	GetLastDailyWish(user string, poolName string) (time.Time, error)
	SetLastDailyWish(user string, poolName string, date time.Time) error
	AddWishHistory(user string, record WishRecord) error
	AddWishHistoryWithConfig(user string, record WishRecord, config *HistoryConfig) error

	// 统计相关方法
	IncrementTotalWishes(user string, poolName string, count int) error
	IncrementGuaranteeCount(user string, poolName string) error
	GetWishStats(user string, poolName string) (totalWishes int, guaranteeCount int, err error)

	// 祈愿券相关方法
	GetWishTickets(user string, ticketType string) (int, error)
	UpdateWishTickets(user string, ticketType string, amount int) error
	AddWishTickets(user string, ticketType string, amount int) error

	// 限制模式相关方法
	GetLimitModeRecord(user string, poolName string) (*LimitModeRecord, error)
	UpdateLimitModeRecord(user string, poolName string, record *LimitModeRecord) error
}

// FileStorage 文件存储实现
type FileStorage struct {
	dataPath string
}

// NewFileStorage 创建文件存储实例
func NewFileStorage(dataPath string) *FileStorage {
	return &FileStorage{
		dataPath: dataPath,
	}
}

// getUserDataPath 获取用户数据文件路径
func (fs *FileStorage) getUserDataPath(user string) string {
	return filepath.Join(fs.dataPath, fmt.Sprintf("%s.yml", user))
}

// InitUserDir 初始化用户目录
func (fs *FileStorage) InitUserDir(user string, dir string) error {
	// 确保数据目录存在
	if _, err := os.Stat(fs.dataPath); os.IsNotExist(err) {
		if err := os.MkdirAll(fs.dataPath, 0755); err != nil {
			return fmt.Errorf("failed to create data directory: %w", err)
		}
	}

	// 确保用户数据文件存在
	_, err := fs.GetUserData(user)
	return err
}

// GetUserData 获取用户数据
func (fs *FileStorage) GetUserData(user string) (*UserData, error) {
	filePath := fs.getUserDataPath(user)

	// 如果文件不存在，创建新的用户数据
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		// 确保目录存在
		if err := os.MkdirAll(filepath.Dir(filePath), 0755); err != nil {
			return nil, fmt.Errorf("failed to create directory: %w", err)
		}

		now := time.Now().Format(time.RFC3339)
		userData := &UserData{
			WishCounts:       make(map[string]int),
			DailyWish:        make(map[string]string),
			LimitModeRecords: make(map[string]LimitModeRecord),
			GuaranteeCounts:  make(map[string]int),
			WishTickets:      make(map[string]int),
			WishHistory:      []WishRecord{},
			CreatedAt:        now,
			UpdatedAt:        now,
		}

		// 初始化统计数据
		userData.WishStats.TotalWishes = make(map[string]int)
		userData.WishStats.GuaranteeCount = make(map[string]int)

		// 保存新创建的用户数据
		if err := fs.SaveUserData(user, userData); err != nil {
			return nil, fmt.Errorf("failed to create user data: %w", err)
		}

		return userData, nil
	}

	// 读取现有文件
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read user data: %w", err)
	}

	var userData UserData
	if err := yaml.Unmarshal(data, &userData); err != nil {
		return nil, fmt.Errorf("failed to parse user data: %w", err)
	}

	// 确保映射已初始化
	if userData.WishCounts == nil {
		userData.WishCounts = make(map[string]int)
	}
	if userData.GuaranteeCounts == nil {
		userData.GuaranteeCounts = make(map[string]int)
	}
	if userData.WishHistory == nil {
		userData.WishHistory = []WishRecord{}
	}
	if userData.DailyWish == nil {
		userData.DailyWish = make(map[string]string)
	}
	if userData.WishTickets == nil {
		userData.WishTickets = make(map[string]int)
	}

	// 确保统计数据已初始化（向后兼容）
	if userData.WishStats.TotalWishes == nil {
		userData.WishStats.TotalWishes = make(map[string]int)
	}
	if userData.WishStats.GuaranteeCount == nil {
		userData.WishStats.GuaranteeCount = make(map[string]int)
	}

	return &userData, nil
}

// SaveUserData 保存用户数据
func (fs *FileStorage) SaveUserData(user string, data *UserData) error {
	filePath := fs.getUserDataPath(user)

	// 确保目录存在
	dir := filepath.Dir(filePath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("failed to create directory: %w", err)
	}

	// 更新时间戳
	data.UpdatedAt = time.Now().Format(time.RFC3339)

	// 序列化为YAML
	yamlData, err := yaml.Marshal(data)
	if err != nil {
		return fmt.Errorf("failed to marshal user data: %w", err)
	}

	// 写入文件
	if err := os.WriteFile(filePath, yamlData, 0644); err != nil {
		return fmt.Errorf("failed to write user data: %w", err)
	}

	return nil
}

// GetWishCount 获取祈愿次数
func (fs *FileStorage) GetWishCount(user string, poolName string) (int, error) {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return 0, err
	}

	count, exists := userData.WishCounts[poolName]
	if !exists {
		return 0, nil
	}

	return count, nil
}

// UpdateWishCount 更新祈愿次数
func (fs *FileStorage) UpdateWishCount(user string, poolName string, count int) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	userData.WishCounts[poolName] = count

	return fs.SaveUserData(user, userData)
}

// GetLastDailyWish 获取指定池的最后一次每日祈愿时间
func (fs *FileStorage) GetLastDailyWish(user string, poolName string) (time.Time, error) {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return time.Time{}, err
	}

	if userData.DailyWish == nil {
		return time.Time{}, nil
	}

	dateStr, exists := userData.DailyWish[poolName]
	if !exists || dateStr == "" {
		return time.Time{}, nil
	}

	date, err := time.Parse("2006-01-02", dateStr)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid date format: %w", err)
	}

	return date, nil
}

// SetLastDailyWish 设置指定池的最后一次每日祈愿时间
func (fs *FileStorage) SetLastDailyWish(user string, poolName string, date time.Time) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.DailyWish == nil {
		userData.DailyWish = make(map[string]string)
	}

	userData.DailyWish[poolName] = date.Format("2006-01-02")

	return fs.SaveUserData(user, userData)
}

// AddWishHistory 添加祈愿历史记录
func (fs *FileStorage) AddWishHistory(user string, record WishRecord) error {
	return fs.AddWishHistoryWithConfig(user, record, nil)
}

// AddWishHistoryWithConfig 添加祈愿历史记录（使用配置）
func (fs *FileStorage) AddWishHistoryWithConfig(user string, record WishRecord, config *HistoryConfig) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	userData.WishHistory = append(userData.WishHistory, record)

	// 使用配置限制历史记录数量
	maxHistorySize := 50 // 默认值
	if config != nil && config.MaxSize > 0 {
		maxHistorySize = config.MaxSize
	}

	if len(userData.WishHistory) > maxHistorySize {
		userData.WishHistory = userData.WishHistory[len(userData.WishHistory)-maxHistorySize:]
	}

	// 清理过期记录
	if config != nil && config.Expiration != "" {
		userData.WishHistory = fs.cleanExpiredHistory(userData.WishHistory, config.Expiration)
	}

	return fs.SaveUserData(user, userData)
}

// cleanExpiredHistory 清理过期的历史记录
func (fs *FileStorage) cleanExpiredHistory(history []WishRecord, expiration string) []WishRecord {
	if expiration == "" {
		return history
	}

	// 解析过期时间
	duration, err := fs.parseExpirationDuration(expiration)
	if err != nil {
		// 如果解析失败，返回原历史记录
		return history
	}

	cutoffTime := time.Now().Add(-duration)
	var validHistory []WishRecord

	for _, record := range history {
		if record.Timestamp.After(cutoffTime) {
			validHistory = append(validHistory, record)
		}
	}

	return validHistory
}

// parseExpirationDuration 解析过期时间字符串
func (fs *FileStorage) parseExpirationDuration(expiration string) (time.Duration, error) {
	// 支持格式：30d, 7d, 24h, 1w, 1m, 1y
	if strings.HasSuffix(expiration, "d") {
		days, err := strconv.Atoi(strings.TrimSuffix(expiration, "d"))
		if err != nil {
			return 0, err
		}
		return time.Duration(days) * 24 * time.Hour, nil
	}

	if strings.HasSuffix(expiration, "w") {
		weeks, err := strconv.Atoi(strings.TrimSuffix(expiration, "w"))
		if err != nil {
			return 0, err
		}
		return time.Duration(weeks) * 7 * 24 * time.Hour, nil
	}

	if strings.HasSuffix(expiration, "m") {
		months, err := strconv.Atoi(strings.TrimSuffix(expiration, "m"))
		if err != nil {
			return 0, err
		}
		return time.Duration(months) * 30 * 24 * time.Hour, nil // 近似值
	}

	if strings.HasSuffix(expiration, "y") {
		years, err := strconv.Atoi(strings.TrimSuffix(expiration, "y"))
		if err != nil {
			return 0, err
		}
		return time.Duration(years) * 365 * 24 * time.Hour, nil // 近似值
	}

	// 默认使用 Go 的 time.ParseDuration (支持 h, m, s 等)
	return time.ParseDuration(expiration)
}

// GetWishTickets 获取用户指定类型的祈愿券数量
func (fs *FileStorage) GetWishTickets(user string, ticketType string) (int, error) {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return 0, err
	}

	if userData.WishTickets == nil {
		userData.WishTickets = make(map[string]int)
	}

	return userData.WishTickets[ticketType], nil
}

// UpdateWishTickets 更新用户指定类型的祈愿券数量（设置为指定值）
func (fs *FileStorage) UpdateWishTickets(user string, ticketType string, amount int) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.WishTickets == nil {
		userData.WishTickets = make(map[string]int)
	}

	userData.WishTickets[ticketType] = amount
	return fs.SaveUserData(user, userData)
}

// AddWishTickets 增加用户指定类型的祈愿券数量（在当前基础上增加）
func (fs *FileStorage) AddWishTickets(user string, ticketType string, amount int) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.WishTickets == nil {
		userData.WishTickets = make(map[string]int)
	}

	userData.WishTickets[ticketType] += amount

	// 确保不会出现负数
	if userData.WishTickets[ticketType] < 0 {
		userData.WishTickets[ticketType] = 0
	}

	return fs.SaveUserData(user, userData)
}

// IncrementTotalWishes 增加累计祈愿次数
func (fs *FileStorage) IncrementTotalWishes(user string, poolName string, count int) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.WishStats.TotalWishes == nil {
		userData.WishStats.TotalWishes = make(map[string]int)
	}

	userData.WishStats.TotalWishes[poolName] += count

	return fs.SaveUserData(user, userData)
}

// IncrementGuaranteeCount 增加保底获得次数
func (fs *FileStorage) IncrementGuaranteeCount(user string, poolName string) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.WishStats.GuaranteeCount == nil {
		userData.WishStats.GuaranteeCount = make(map[string]int)
	}

	userData.WishStats.GuaranteeCount[poolName]++

	return fs.SaveUserData(user, userData)
}

// GetWishStats 获取祈愿统计数据
func (fs *FileStorage) GetWishStats(user string, poolName string) (totalWishes int, guaranteeCount int, err error) {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return 0, 0, err
	}

	totalWishes = userData.WishStats.TotalWishes[poolName]
	guaranteeCount = userData.WishStats.GuaranteeCount[poolName]

	return totalWishes, guaranteeCount, nil
}

// GetLimitModeRecord 获取限制模式记录
func (fs *FileStorage) GetLimitModeRecord(user string, poolName string) (*LimitModeRecord, error) {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return nil, err
	}

	if userData.LimitModeRecords == nil {
		userData.LimitModeRecords = make(map[string]LimitModeRecord)
	}

	record, exists := userData.LimitModeRecords[poolName]
	if !exists {
		// 返回空记录
		return &LimitModeRecord{}, nil
	}

	return &record, nil
}

// UpdateLimitModeRecord 更新限制模式记录
func (fs *FileStorage) UpdateLimitModeRecord(user string, poolName string, record *LimitModeRecord) error {
	userData, err := fs.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.LimitModeRecords == nil {
		userData.LimitModeRecords = make(map[string]LimitModeRecord)
	}

	userData.LimitModeRecords[poolName] = *record

	return fs.SaveUserData(user, userData)
}
