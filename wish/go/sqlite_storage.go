package wish

import (
	"fmt"
	"time"
)

// SQLiteStorage SQLite 数据库存储实现
// 注意：此版本暂时不支持新的模块化数据结构，建议使用 FileStorage
type SQLiteStorage struct {
}

// NewSQLiteStorage 创建新的 SQLite 存储实例
func NewSQLiteStorage(dbPath string) (*SQLiteStorage, error) {
	return nil, fmt.Errorf("SQLite storage temporarily disabled for modular data structure migration")
}

// 以下是存根方法，满足 Storage 接口要求

func (s *SQLiteStorage) InitUserDir(user string, dir string) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) GetUserData(user string) (*UserData, error) {
	return nil, fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) SaveUserData(user string, data *UserData) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) GetWishCount(user string, poolName string) (int, error) {
	return 0, fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) UpdateWishCount(user string, poolName string, count int) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) GetLastDailyWish(user string, poolName string) (time.Time, error) {
	return time.Time{}, fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) SetLastDailyWish(user string, poolName string, date time.Time) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) AddWishHistory(user string, record WishRecord) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) AddWishHistoryWithConfig(user string, record WishRecord, config *HistoryConfig) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) IncrementTotalWishes(user string, poolName string, count int) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) IncrementGuaranteeCount(user string, poolName string) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) GetWishStats(user string, poolName string) (totalWishes int, guaranteeCount int, err error) {
	return 0, 0, fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) GetWishTickets(user string, ticketType string) (int, error) {
	return 0, fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) UpdateWishTickets(user string, ticketType string, amount int) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) AddWishTickets(user string, ticketType string, amount int) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) GetLimitModeRecord(user string, poolName string) (*LimitModeRecord, error) {
	return nil, fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) UpdateLimitModeRecord(user string, poolName string, record *LimitModeRecord) error {
	return fmt.Errorf("SQLite storage temporarily disabled")
}

func (s *SQLiteStorage) Close() error {
	return nil
}
