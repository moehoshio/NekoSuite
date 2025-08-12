package wish

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// SQLiteStorage SQLite 数据库存储实现
type SQLiteStorage struct {
	db *sql.DB
}

// NewSQLiteStorage 创建新的 SQLite 存储实例
func NewSQLiteStorage(dbPath string) (*SQLiteStorage, error) {
	// 确保目录存在
	dir := filepath.Dir(dbPath)
	if err := ensureDir(dir); err != nil {
		return nil, fmt.Errorf("failed to create database directory: %v", err)
	}

	// 打开数据库连接
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %v", err)
	}

	// 设置数据库参数
	db.SetMaxOpenConns(1) // SQLite 在 WAL 模式下可以支持并发读，但为了安全起见设置为1
	db.SetMaxIdleConns(1)

	storage := &SQLiteStorage{db: db}

	// 初始化数据库表
	if err := storage.initTables(); err != nil {
		return nil, fmt.Errorf("failed to initialize database tables: %v", err)
	}

	return storage, nil
}

// initTables 初始化数据库表
func (s *SQLiteStorage) initTables() error {
	// 用户数据表
	userDataTable := `
	CREATE TABLE IF NOT EXISTS user_data (
		user_id TEXT PRIMARY KEY,
		wish_counts TEXT NOT NULL DEFAULT '{}',
		limit_mode_records TEXT NOT NULL DEFAULT '{}',
		wish_stats TEXT NOT NULL DEFAULT '{}',
		wish_tickets TEXT NOT NULL DEFAULT '{}',
		created_at DATETIME NOT NULL,
		updated_at DATETIME NOT NULL
	);`

	// 祈愿历史表
	wishHistoryTable := `
	CREATE TABLE IF NOT EXISTS wish_history (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		user_id TEXT NOT NULL,
		pool TEXT NOT NULL,
		timestamp DATETIME NOT NULL,
		items TEXT NOT NULL,
		count INTEGER NOT NULL,
		cost INTEGER NOT NULL,
		tickets_used TEXT,
		created_at DATETIME NOT NULL,
		FOREIGN KEY (user_id) REFERENCES user_data(user_id)
	);`

	// 创建索引
	indexes := []string{
		`CREATE INDEX IF NOT EXISTS idx_wish_history_user_id ON wish_history(user_id);`,
		`CREATE INDEX IF NOT EXISTS idx_wish_history_pool ON wish_history(pool);`,
		`CREATE INDEX IF NOT EXISTS idx_wish_history_timestamp ON wish_history(timestamp);`,
	}

	// 执行创建表语句
	if _, err := s.db.Exec(userDataTable); err != nil {
		return fmt.Errorf("failed to create user_data table: %v", err)
	}

	if _, err := s.db.Exec(wishHistoryTable); err != nil {
		return fmt.Errorf("failed to create wish_history table: %v", err)
	}

	// 创建索引
	for _, index := range indexes {
		if _, err := s.db.Exec(index); err != nil {
			return fmt.Errorf("failed to create index: %v", err)
		}
	}

	return nil
}

// InitUserDir SQLite 不需要用户目录，空实现
func (s *SQLiteStorage) InitUserDir(user string, dir string) error {
	return nil
}

// GetUserData 获取用户数据
func (s *SQLiteStorage) GetUserData(user string) (*UserData, error) {
	query := `
	SELECT wish_counts, limit_mode_records, wish_stats, wish_tickets, created_at, updated_at
	FROM user_data WHERE user_id = ?`

	var wishCountsJSON, limitModeRecordsJSON, wishStatsJSON, wishTicketsJSON string
	var createdAt, updatedAt time.Time

	err := s.db.QueryRow(query, user).Scan(
		&wishCountsJSON, &limitModeRecordsJSON, &wishStatsJSON, &wishTicketsJSON,
		&createdAt, &updatedAt,
	)

	if err != nil {
		if err == sql.ErrNoRows {
			// 用户不存在，创建新用户数据
			userData := &UserData{
				WishCounts:       make(map[string]int),
				LimitModeRecords: make(map[string]LimitModeRecord),
				WishTickets:      make(map[string]int),
				CreatedAt:        time.Now().Format(time.RFC3339),
				UpdatedAt:        time.Now().Format(time.RFC3339),
			}
			userData.WishStats.TotalWishes = make(map[string]int)
			userData.WishStats.GuaranteeCount = make(map[string]int)

			return userData, nil
		}
		return nil, fmt.Errorf("failed to get user data: %v", err)
	}

	// 解析 JSON 数据
	userData := &UserData{
		WishCounts:       make(map[string]int),
		LimitModeRecords: make(map[string]LimitModeRecord),
		WishTickets:      make(map[string]int),
		CreatedAt:        createdAt.Format(time.RFC3339),
		UpdatedAt:        updatedAt.Format(time.RFC3339),
	}
	userData.WishStats.TotalWishes = make(map[string]int)
	userData.WishStats.GuaranteeCount = make(map[string]int)

	if err := json.Unmarshal([]byte(wishCountsJSON), &userData.WishCounts); err != nil {
		return nil, fmt.Errorf("failed to unmarshal wish_counts: %v", err)
	}

	if err := json.Unmarshal([]byte(limitModeRecordsJSON), &userData.LimitModeRecords); err != nil {
		return nil, fmt.Errorf("failed to unmarshal limit_mode_records: %v", err)
	}

	if err := json.Unmarshal([]byte(wishStatsJSON), &userData.WishStats); err != nil {
		return nil, fmt.Errorf("failed to unmarshal wish_stats: %v", err)
	}

	if err := json.Unmarshal([]byte(wishTicketsJSON), &userData.WishTickets); err != nil {
		return nil, fmt.Errorf("failed to unmarshal wish_tickets: %v", err)
	}

	// 获取祈愿历史
	historyQuery := `
	SELECT pool, timestamp, items, count, cost, COALESCE(tickets_used, '{}')
	FROM wish_history WHERE user_id = ? ORDER BY timestamp DESC`

	rows, err := s.db.Query(historyQuery, user)
	if err != nil {
		return nil, fmt.Errorf("failed to get wish history: %v", err)
	}
	defer rows.Close()

	for rows.Next() {
		var record WishRecord
		var itemsJSON, ticketsUsedJSON string
		var timestamp time.Time

		if err := rows.Scan(&record.Pool, &timestamp, &itemsJSON, &record.Count, &record.Cost, &ticketsUsedJSON); err != nil {
			return nil, fmt.Errorf("failed to scan wish history row: %v", err)
		}

		record.Timestamp = timestamp

		if err := json.Unmarshal([]byte(itemsJSON), &record.Items); err != nil {
			return nil, fmt.Errorf("failed to unmarshal wish items: %v", err)
		}

		if ticketsUsedJSON != "{}" && ticketsUsedJSON != "" {
			if err := json.Unmarshal([]byte(ticketsUsedJSON), &record.TicketsUsed); err != nil {
				return nil, fmt.Errorf("failed to unmarshal tickets used: %v", err)
			}
		}

		userData.WishHistory = append(userData.WishHistory, record)
	}

	return userData, nil
}

// SaveUserData 保存用户数据
func (s *SQLiteStorage) SaveUserData(user string, data *UserData) error {
	// 序列化 JSON 数据
	wishCountsJSON, err := json.Marshal(data.WishCounts)
	if err != nil {
		return fmt.Errorf("failed to marshal wish_counts: %v", err)
	}

	limitModeRecordsJSON, err := json.Marshal(data.LimitModeRecords)
	if err != nil {
		return fmt.Errorf("failed to marshal limit_mode_records: %v", err)
	}

	wishStatsJSON, err := json.Marshal(data.WishStats)
	if err != nil {
		return fmt.Errorf("failed to marshal wish_stats: %v", err)
	}

	wishTicketsJSON, err := json.Marshal(data.WishTickets)
	if err != nil {
		return fmt.Errorf("failed to marshal wish_tickets: %v", err)
	}

	// 更新时间
	data.UpdatedAt = time.Now().Format(time.RFC3339)

	// 使用 UPSERT 语句
	query := `
	INSERT INTO user_data (user_id, wish_counts, limit_mode_records, wish_stats, wish_tickets, created_at, updated_at)
	VALUES (?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(user_id) DO UPDATE SET
		wish_counts = excluded.wish_counts,
		limit_mode_records = excluded.limit_mode_records,
		wish_stats = excluded.wish_stats,
		wish_tickets = excluded.wish_tickets,
		updated_at = excluded.updated_at`

	createdAt := data.CreatedAt
	if createdAt == "" {
		createdAt = time.Now().Format(time.RFC3339)
		data.CreatedAt = createdAt
	}

	_, err = s.db.Exec(query, user, string(wishCountsJSON), string(limitModeRecordsJSON),
		string(wishStatsJSON), string(wishTicketsJSON), createdAt, data.UpdatedAt)

	if err != nil {
		return fmt.Errorf("failed to save user data: %v", err)
	}

	return nil
}

// GetWishCount 获取祈愿次数
func (s *SQLiteStorage) GetWishCount(user string, poolName string) (int, error) {
	userData, err := s.GetUserData(user)
	if err != nil {
		return 0, err
	}
	return userData.WishCounts[poolName], nil
}

// UpdateWishCount 更新祈愿次数
func (s *SQLiteStorage) UpdateWishCount(user string, poolName string, count int) error {
	userData, err := s.GetUserData(user)
	if err != nil {
		return err
	}

	userData.WishCounts[poolName] = count
	return s.SaveUserData(user, userData)
}

// GetLastDailyWish 获取最后每日祈愿时间 (这个方法在 SQLite 中使用 LimitModeRecord 实现)
func (s *SQLiteStorage) GetLastDailyWish(user string, poolName string) (time.Time, error) {
	record, err := s.GetLimitModeRecord(user, poolName)
	if err != nil {
		return time.Time{}, err
	}
	if record == nil {
		return time.Time{}, nil
	}
	return record.CurrentPeriodStart, nil
}

// SetLastDailyWish 设置最后每日祈愿时间 (这个方法在 SQLite 中使用 LimitModeRecord 实现)
func (s *SQLiteStorage) SetLastDailyWish(user string, poolName string, date time.Time) error {
	record, err := s.GetLimitModeRecord(user, poolName)
	if err != nil {
		return err
	}
	if record == nil {
		record = &LimitModeRecord{
			CurrentPeriodStart: date,
			CurrentPeriodEnd:   date.Add(24 * time.Hour),
			UsedCount:          0,
		}
	} else {
		record.CurrentPeriodStart = date
	}
	return s.UpdateLimitModeRecord(user, poolName, record)
}

// AddWishHistory 添加祈愿历史记录
func (s *SQLiteStorage) AddWishHistory(user string, record WishRecord) error {
	return s.AddWishHistoryWithConfig(user, record, nil)
}

// AddWishHistoryWithConfig 添加祈愿历史记录（带配置）
func (s *SQLiteStorage) AddWishHistoryWithConfig(user string, record WishRecord, config *HistoryConfig) error {
	itemsJSON, err := json.Marshal(record.Items)
	if err != nil {
		return fmt.Errorf("failed to marshal wish items: %v", err)
	}

	ticketsUsedJSON, err := json.Marshal(record.TicketsUsed)
	if err != nil {
		return fmt.Errorf("failed to marshal tickets used: %v", err)
	}

	query := `
	INSERT INTO wish_history (user_id, pool, timestamp, items, count, cost, tickets_used, created_at)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?)`

	_, err = s.db.Exec(query, user, record.Pool, record.Timestamp, string(itemsJSON),
		record.Count, record.Cost, string(ticketsUsedJSON), time.Now())

	if err != nil {
		return fmt.Errorf("failed to add wish history: %v", err)
	}

	// 如果有配置，清理过期记录
	if config != nil && config.MaxSize > 0 {
		if err := s.cleanOldHistory(user, config.MaxSize); err != nil {
			return fmt.Errorf("failed to clean old history: %v", err)
		}
	}

	if config != nil && config.Expiration != "" {
		if err := s.cleanExpiredHistoryDB(user, config.Expiration); err != nil {
			return fmt.Errorf("failed to clean expired history: %v", err)
		}
	}

	return nil
}

// cleanOldHistory 清理超出数量限制的旧记录
func (s *SQLiteStorage) cleanOldHistory(user string, maxSize int) error {
	query := `
	DELETE FROM wish_history 
	WHERE user_id = ? AND id NOT IN (
		SELECT id FROM wish_history 
		WHERE user_id = ? 
		ORDER BY timestamp DESC 
		LIMIT ?
	)`

	_, err := s.db.Exec(query, user, user, maxSize)
	return err
}

// cleanExpiredHistoryDB 清理过期记录
func (s *SQLiteStorage) cleanExpiredHistoryDB(user string, expiration string) error {
	duration, err := s.parseExpirationDuration(expiration)
	if err != nil {
		return err
	}

	expiredTime := time.Now().Add(-duration)
	query := `DELETE FROM wish_history WHERE user_id = ? AND timestamp < ?`

	_, err = s.db.Exec(query, user, expiredTime)
	return err
}

// parseExpirationDuration 解析过期时间配置
func (s *SQLiteStorage) parseExpirationDuration(expiration string) (time.Duration, error) {
	// 这里复用 FileStorage 的实现逻辑
	fs := &FileStorage{}
	return fs.parseExpirationDuration(expiration)
}

// IncrementTotalWishes 增加总祈愿次数
func (s *SQLiteStorage) IncrementTotalWishes(user string, poolName string, count int) error {
	userData, err := s.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.WishStats.TotalWishes == nil {
		userData.WishStats.TotalWishes = make(map[string]int)
	}
	userData.WishStats.TotalWishes[poolName] += count

	return s.SaveUserData(user, userData)
}

// IncrementGuaranteeCount 增加保底次数
func (s *SQLiteStorage) IncrementGuaranteeCount(user string, poolName string) error {
	userData, err := s.GetUserData(user)
	if err != nil {
		return err
	}

	if userData.WishStats.GuaranteeCount == nil {
		userData.WishStats.GuaranteeCount = make(map[string]int)
	}
	userData.WishStats.GuaranteeCount[poolName]++

	return s.SaveUserData(user, userData)
}

// GetWishStats 获取祈愿统计
func (s *SQLiteStorage) GetWishStats(user string, poolName string) (totalWishes int, guaranteeCount int, err error) {
	userData, err := s.GetUserData(user)
	if err != nil {
		return 0, 0, err
	}

	if userData.WishStats.TotalWishes != nil {
		totalWishes = userData.WishStats.TotalWishes[poolName]
	}
	if userData.WishStats.GuaranteeCount != nil {
		guaranteeCount = userData.WishStats.GuaranteeCount[poolName]
	}

	return totalWishes, guaranteeCount, nil
}

// GetWishTickets 获取祈愿券数量
func (s *SQLiteStorage) GetWishTickets(user string, ticketType string) (int, error) {
	userData, err := s.GetUserData(user)
	if err != nil {
		return 0, err
	}
	return userData.WishTickets[ticketType], nil
}

// UpdateWishTickets 更新祈愿券数量
func (s *SQLiteStorage) UpdateWishTickets(user string, ticketType string, amount int) error {
	userData, err := s.GetUserData(user)
	if err != nil {
		return err
	}

	userData.WishTickets[ticketType] = amount
	return s.SaveUserData(user, userData)
}

// AddWishTickets 增加祈愿券数量
func (s *SQLiteStorage) AddWishTickets(user string, ticketType string, amount int) error {
	userData, err := s.GetUserData(user)
	if err != nil {
		return err
	}

	userData.WishTickets[ticketType] += amount
	return s.SaveUserData(user, userData)
}

// GetLimitModeRecord 获取限制模式记录
func (s *SQLiteStorage) GetLimitModeRecord(user string, poolName string) (*LimitModeRecord, error) {
	userData, err := s.GetUserData(user)
	if err != nil {
		return nil, err
	}

	if record, exists := userData.LimitModeRecords[poolName]; exists {
		return &record, nil
	}
	// 返回空记录而不是 nil，与 FileStorage 行为一致
	return &LimitModeRecord{}, nil
}

// UpdateLimitModeRecord 更新限制模式记录
func (s *SQLiteStorage) UpdateLimitModeRecord(user string, poolName string, record *LimitModeRecord) error {
	userData, err := s.GetUserData(user)
	if err != nil {
		return err
	}

	userData.LimitModeRecords[poolName] = *record
	return s.SaveUserData(user, userData)
}

// Close 关闭数据库连接
func (s *SQLiteStorage) Close() error {
	if s.db != nil {
		return s.db.Close()
	}
	return nil
}
