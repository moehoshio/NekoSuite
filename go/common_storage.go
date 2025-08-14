package common

import (
	"strings"
	"time"
)

// CommonStorage 通用存儲接口
type CommonStorage interface {
	// 獲取用戶數據
	GetUserData(userID string) (*UserData, error)
	// 保存用戶數據
	SaveUserData(userID string, data *UserData) error

	// 模塊特定數據存取
	GetModuleData(userID, moduleName string, result interface{}) error
	SetModuleData(userID, moduleName string, data interface{}) error

	// 限制模式記錄 (帶模塊前綴)
	GetLimitModeRecord(userID, moduleName, poolName string) (*LimitModeRecord, error)
	UpdateLimitModeRecord(userID, moduleName, poolName string, record *LimitModeRecord) error

	// 清理過期數據
	CleanupExpiredData() error

	// 關閉存儲
	Close() error
}

// UserData 通用用戶數據結構
type UserData struct {
	// 用戶創建時間和最後更新時間
	CreatedAt string `yaml:"created_at" json:"created_at"`
	UpdatedAt string `yaml:"updated_at" json:"updated_at"`

	// 各模塊的數據都存儲在對應的鍵下
	// 例如: wish 模塊的數據存在 "wish" 鍵下
	//      event 模塊的數據存在 "event" 鍵下
	ModuleData map[string]interface{} `yaml:",inline"`
}

// LimitModeRecord 限制模式記錄
type LimitModeRecord struct {
	CurrentPeriodStart time.Time `yaml:"current_period_start" json:"current_period_start"` // 當前周期開始時間
	CurrentPeriodEnd   time.Time `yaml:"current_period_end" json:"current_period_end"`     // 當前周期結束時間
	UsedCount          int       `yaml:"used_count" json:"used_count"`                     // 當前周期已使用次數
	LastRefreshTime    time.Time `yaml:"last_refresh_time" json:"last_refresh_time"`       // 最後刷新時間
}

// LimitModeConfig 限制模式配置
type LimitModeConfig struct {
	Count          int    `yaml:"count" json:"count"`                                           // 每個周期的次數限制
	Time           string `yaml:"time" json:"time"`                                             // 周期時間（如 "1d", "1w", "1m"）
	RefreshAtTime  string `yaml:"refresh_at_time,omitempty" json:"refresh_at_time,omitempty"`   // 每天的刷新時間，格式為 HH:mm
	RefreshAtWeek  int    `yaml:"refresh_at_week,omitempty" json:"refresh_at_week,omitempty"`   // 每週的刷新時間，1-7表示週一到週日
	RefreshAtMonth int    `yaml:"refresh_at_month,omitempty" json:"refresh_at_month,omitempty"` // 每月的刷新時間，1-31表示每月的某天
}

// DurationConfig 持續時間配置
type DurationConfig struct {
	StartDate string `yaml:"startDate" json:"start_date"` // 活動開始時間
	EndDate   string `yaml:"endDate" json:"end_date"`     // 活動結束時間
}

// RewardConfig 獎勵配置
type RewardConfig struct {
	Type   string      `yaml:"type" json:"type"`                         // 獎勵類型：balance, exp, item, command
	Value  interface{} `yaml:"value" json:"value"`                       // 獎勵值
	Amount int         `yaml:"amount,omitempty" json:"amount,omitempty"` // 數量（用於物品）
}

// 輔助函數：構造帶模塊前綴的鍵
func MakeModuleKey(moduleName, key string) string {
	return moduleName + ":" + key
}

// 輔助函數：從帶前綴的鍵中提取模塊名和原始鍵
func ParseModuleKey(moduleKey string) (moduleName, key string) {
	parts := strings.SplitN(moduleKey, ":", 2)
	if len(parts) == 2 {
		return parts[0], parts[1]
	}
	return "", moduleKey
}
