package wish

import (
	"fmt"
	"time"

	common "neko-suite/go"
)

// CreateStorage 根據配置創建存儲實例（舊版兼容）
func CreateStorage(config *Config) (Storage, error) {
	// 創建統一存儲
	commonStorage, err := common.NewStorage(config.Storage)
	if err != nil {
		return nil, err
	}

	// 包裝為舊接口（如果需要的話）
	return NewLegacyStorageWrapper(commonStorage), nil
}

// LegacyStorageWrapper 舊存儲接口的包裝器
type LegacyStorageWrapper struct {
	storage *WishStorageAdapter
}

// NewLegacyStorageWrapper 創建舊存儲接口包裝器
func NewLegacyStorageWrapper(commonStorage common.CommonStorage) *LegacyStorageWrapper {
	return &LegacyStorageWrapper{
		storage: NewWishStorageAdapter(commonStorage),
	}
}

// 實現舊的 Storage 接口方法（僅實現必要的方法）
func (w *LegacyStorageWrapper) InitUserDir(user string, dir string) error {
	// 統一存儲不需要初始化用戶目錄
	return nil
}

func (w *LegacyStorageWrapper) GetUserData(user string) (*UserData, error) {
	// 返回nil，因為新版本不使用這個方法
	return nil, fmt.Errorf("deprecated method, use unified storage instead")
}

func (w *LegacyStorageWrapper) SaveUserData(user string, data *UserData) error {
	// 返回錯誤，因為新版本不使用這個方法
	return fmt.Errorf("deprecated method, use unified storage instead")
}

func (w *LegacyStorageWrapper) GetWishCount(user string, poolName string) (int, error) {
	return w.storage.GetWishCount(user, poolName)
}

func (w *LegacyStorageWrapper) UpdateWishCount(user string, poolName string, count int) error {
	return w.storage.UpdateWishCount(user, poolName, count)
}

func (w *LegacyStorageWrapper) GetLastDailyWish(user string, poolName string) (time.Time, error) {
	// 這個方法在統一存儲中不再需要，因為使用了更靈活的限制模式
	return time.Time{}, nil
}

func (w *LegacyStorageWrapper) SetLastDailyWish(user string, poolName string, date time.Time) error {
	// 這個方法在統一存儲中不再需要
	return nil
}

func (w *LegacyStorageWrapper) AddWishHistory(user string, record WishRecord) error {
	// 轉換為統一格式
	unifiedRecord := UnifiedWishRecord{
		Pool:        record.Pool,
		Count:       record.Count,
		Items:       record.Items,
		Cost:        record.Cost,
		TicketsUsed: record.TicketsUsed,
		Timestamp:   record.Timestamp,
	}
	return w.storage.AddWishHistory(user, unifiedRecord)
}

func (w *LegacyStorageWrapper) AddWishHistoryWithConfig(user string, record WishRecord, config *HistoryConfig) error {
	return w.AddWishHistory(user, record)
}

func (w *LegacyStorageWrapper) IncrementTotalWishes(user string, poolName string, count int) error {
	return w.storage.IncrementTotalWishes(user, poolName, count)
}

func (w *LegacyStorageWrapper) IncrementGuaranteeCount(user string, poolName string) error {
	return w.storage.IncrementGuaranteeCount(user, poolName)
}

func (w *LegacyStorageWrapper) GetWishStats(user string, poolName string) (totalWishes int, guaranteeCount int, err error) {
	return w.storage.GetWishStats(user, poolName)
}

func (w *LegacyStorageWrapper) GetWishTickets(user string, ticketType string) (int, error) {
	return w.storage.GetWishTickets(user, ticketType)
}

func (w *LegacyStorageWrapper) UpdateWishTickets(user string, ticketType string, amount int) error {
	return w.storage.UpdateWishTickets(user, ticketType, amount)
}

func (w *LegacyStorageWrapper) AddWishTickets(user string, ticketType string, amount int) error {
	return w.storage.AddWishTickets(user, ticketType, amount)
}

func (w *LegacyStorageWrapper) GetLimitModeRecord(user string, poolName string) (*LimitModeRecord, error) {
	record, err := w.storage.GetLimitModeRecord(user, poolName)
	if err != nil {
		return nil, err
	}
	if record == nil {
		return nil, nil
	}
	// 轉換為舊格式
	return &LimitModeRecord{
		CurrentPeriodStart: record.CurrentPeriodStart,
		CurrentPeriodEnd:   record.CurrentPeriodEnd,
		UsedCount:          record.UsedCount,
		LastRefreshTime:    record.LastRefreshTime,
	}, nil
}

func (w *LegacyStorageWrapper) UpdateLimitModeRecord(user string, poolName string, record *LimitModeRecord) error {
	// 轉換為統一格式
	unifiedRecord := &common.LimitModeRecord{
		CurrentPeriodStart: record.CurrentPeriodStart,
		CurrentPeriodEnd:   record.CurrentPeriodEnd,
		UsedCount:          record.UsedCount,
		LastRefreshTime:    record.LastRefreshTime,
	}
	return w.storage.UpdateLimitModeRecord(user, poolName, unifiedRecord)
}
