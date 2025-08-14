package wish

import (
	"time"

	common "neko-suite/go"
)

// UnifiedWishModuleData wish 模塊的統一數據結構
type UnifiedWishModuleData struct {
	// 祈願次數統計 (池名 -> 次數，用於保底計算)
	WishCounts map[string]int `yaml:"wish_counts" json:"wish_counts"`

	// 限制模式記錄 (池名 -> 周期使用記錄)
	LimitModeRecords map[string]*common.LimitModeRecord `yaml:"limit_mode_records,omitempty" json:"limit_mode_records,omitempty"`

	// 祈願統計 (累計數據，不會因保底重置)
	Stats struct {
		// 累計祈願次數 (池名 -> 總次數)
		TotalWishes map[string]int `yaml:"total_wishes" json:"total_wishes"`
		// 獲得保底次數 (池名 -> 保底次數)
		GuaranteeCount map[string]int `yaml:"guarantee_count" json:"guarantee_count"`
	} `yaml:"stats" json:"stats"`

	// 祈願券數量 (券類型 -> 數量)
	Tickets map[string]int `yaml:"tickets" json:"tickets"`

	// 祈願歷史記錄 (可選，用於統計分析)
	History []UnifiedWishRecord `yaml:"history,omitempty" json:"history,omitempty"`
}

// UnifiedWishRecord 統一的單次祈願記錄
type UnifiedWishRecord struct {
	Pool        string         `yaml:"pool" json:"pool"`                           // 祈願池
	Count       int            `yaml:"count" json:"count"`                         // 祈願次數
	Items       []string       `yaml:"items" json:"items"`                         // 獲得物品
	Cost        int            `yaml:"cost" json:"cost"`                           // 花費
	TicketsUsed map[string]int `yaml:"tickets_used,omitempty" json:"tickets_used"` // 使用的祈願券詳情
	Timestamp   time.Time      `yaml:"timestamp" json:"timestamp"`                 // 祈願時間
}

// WishStorageAdapter wish 存儲接口適配器
type WishStorageAdapter struct {
	storage common.CommonStorage
}

// NewWishStorageAdapter 創建 wish 存儲適配器
func NewWishStorageAdapter(storage common.CommonStorage) *WishStorageAdapter {
	return &WishStorageAdapter{
		storage: storage,
	}
}

// GetWishData 獲取 wish 模塊數據
func (s *WishStorageAdapter) GetWishData(userID string) (*UnifiedWishModuleData, error) {
	var wishData UnifiedWishModuleData
	err := s.storage.GetModuleData(userID, "wish", &wishData)
	if err != nil {
		return nil, err
	}

	// 初始化空的 map 和 slice
	if wishData.WishCounts == nil {
		wishData.WishCounts = make(map[string]int)
	}
	if wishData.LimitModeRecords == nil {
		wishData.LimitModeRecords = make(map[string]*common.LimitModeRecord)
	}
	if wishData.Stats.TotalWishes == nil {
		wishData.Stats.TotalWishes = make(map[string]int)
	}
	if wishData.Stats.GuaranteeCount == nil {
		wishData.Stats.GuaranteeCount = make(map[string]int)
	}
	if wishData.Tickets == nil {
		wishData.Tickets = make(map[string]int)
	}
	if wishData.History == nil {
		wishData.History = make([]UnifiedWishRecord, 0)
	}

	return &wishData, nil
}

// SaveWishData 保存 wish 模塊數據
func (s *WishStorageAdapter) SaveWishData(userID string, data *UnifiedWishModuleData) error {
	return s.storage.SetModuleData(userID, "wish", data)
}

// GetWishCount 獲取祈願次數
func (s *WishStorageAdapter) GetWishCount(userID, poolName string) (int, error) {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return 0, err
	}
	return wishData.WishCounts[poolName], nil
}

// UpdateWishCount 更新祈願次數
func (s *WishStorageAdapter) UpdateWishCount(userID, poolName string, count int) error {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return err
	}
	wishData.WishCounts[poolName] = count
	return s.SaveWishData(userID, wishData)
}

// GetWishStats 獲取祈願統計
func (s *WishStorageAdapter) GetWishStats(userID, poolName string) (totalWishes int, guaranteeCount int, err error) {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return 0, 0, err
	}
	return wishData.Stats.TotalWishes[poolName], wishData.Stats.GuaranteeCount[poolName], nil
}

// IncrementTotalWishes 增加總祈願次數
func (s *WishStorageAdapter) IncrementTotalWishes(userID, poolName string, count int) error {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return err
	}
	wishData.Stats.TotalWishes[poolName] += count
	return s.SaveWishData(userID, wishData)
}

// IncrementGuaranteeCount 增加保底次數
func (s *WishStorageAdapter) IncrementGuaranteeCount(userID, poolName string) error {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return err
	}
	wishData.Stats.GuaranteeCount[poolName]++
	return s.SaveWishData(userID, wishData)
}

// GetWishTickets 獲取祈願券數量
func (s *WishStorageAdapter) GetWishTickets(userID, ticketType string) (int, error) {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return 0, err
	}
	return wishData.Tickets[ticketType], nil
}

// UpdateWishTickets 更新祈願券數量
func (s *WishStorageAdapter) UpdateWishTickets(userID, ticketType string, amount int) error {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return err
	}
	wishData.Tickets[ticketType] = amount
	return s.SaveWishData(userID, wishData)
}

// AddWishTickets 增加祈願券數量
func (s *WishStorageAdapter) AddWishTickets(userID, ticketType string, amount int) error {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return err
	}
	wishData.Tickets[ticketType] += amount
	return s.SaveWishData(userID, wishData)
}

// AddWishHistory 添加祈願歷史記錄
func (s *WishStorageAdapter) AddWishHistory(userID string, record UnifiedWishRecord) error {
	wishData, err := s.GetWishData(userID)
	if err != nil {
		return err
	}
	wishData.History = append(wishData.History, record)
	return s.SaveWishData(userID, wishData)
}

// GetLimitModeRecord 獲取限制模式記錄
func (s *WishStorageAdapter) GetLimitModeRecord(userID, poolName string) (*common.LimitModeRecord, error) {
	return s.storage.GetLimitModeRecord(userID, "wish", poolName)
}

// UpdateLimitModeRecord 更新限制模式記錄
func (s *WishStorageAdapter) UpdateLimitModeRecord(userID, poolName string, record *common.LimitModeRecord) error {
	return s.storage.UpdateLimitModeRecord(userID, "wish", poolName, record)
}
