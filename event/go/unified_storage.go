package event

import (
	"time"

	common "neko-suite/go"
)

// EventModuleData event 模塊的數據結構
type EventModuleData struct {
	// 限制模式記錄 (活動ID:池名 -> 周期使用記錄)
	LimitModeRecords map[string]*common.LimitModeRecord `yaml:"limit_mode_records,omitempty" json:"limit_mode_records,omitempty"`

	// 各活動的用戶數據 (活動ID -> 活動數據)
	Events map[string]*UnifiedUserEventData `yaml:"events,omitempty" json:"events,omitempty"`
}

// UnifiedUserEventData 統一的用戶活動數據
type UnifiedUserEventData struct {
	// 最後參與時間
	LastParticipateTime time.Time `yaml:"last_participate_time" json:"last_participate_time"`

	// 累積參與次數
	TotalParticipations int `yaml:"total_participations" json:"total_participations"`

	// 連續參與天數（對於簽到類活動）
	ConsecutiveDays int `yaml:"consecutive_days" json:"consecutive_days"`

	// 已領取的獎勵記錄
	ClaimedRewards []UnifiedRewardRecord `yaml:"claimed_rewards" json:"claimed_rewards"`

	// 活動進度數據
	Progress map[string]interface{} `yaml:"progress,omitempty" json:"progress,omitempty"`

	// 創建和更新時間
	CreatedAt string `yaml:"created_at" json:"created_at"`
	UpdatedAt string `yaml:"updated_at" json:"updated_at"`
}

// UnifiedRewardRecord 統一的獎勵記錄
type UnifiedRewardRecord struct {
	RewardID    string    `yaml:"reward_id" json:"reward_id"`     // 獎勵ID
	RewardType  string    `yaml:"reward_type" json:"reward_type"` // 獎勵類型
	ClaimedAt   time.Time `yaml:"claimed_at" json:"claimed_at"`   // 領取時間
	Amount      int       `yaml:"amount" json:"amount"`           // 獎勵數量
	Description string    `yaml:"description" json:"description"` // 獎勵描述
}

// EventStorageAdapter event 存儲接口適配器
type EventStorageAdapter struct {
	storage common.CommonStorage
}

// NewEventStorageAdapter 創建 event 存儲適配器
func NewEventStorageAdapter(storage common.CommonStorage) *EventStorageAdapter {
	return &EventStorageAdapter{
		storage: storage,
	}
}

// GetEventModuleData 獲取 event 模塊數據
func (s *EventStorageAdapter) GetEventModuleData(userID string) (*EventModuleData, error) {
	var eventData EventModuleData
	err := s.storage.GetModuleData(userID, "event", &eventData)
	if err != nil {
		return nil, err
	}

	// 初始化空的 map
	if eventData.LimitModeRecords == nil {
		eventData.LimitModeRecords = make(map[string]*common.LimitModeRecord)
	}
	if eventData.Events == nil {
		eventData.Events = make(map[string]*UnifiedUserEventData)
	}

	return &eventData, nil
}

// SaveEventModuleData 保存 event 模塊數據
func (s *EventStorageAdapter) SaveEventModuleData(userID string, data *EventModuleData) error {
	return s.storage.SetModuleData(userID, "event", data)
}

// GetEventData 獲取指定活動的用戶數據
func (s *EventStorageAdapter) GetEventData(userID, eventID string) (*UnifiedUserEventData, error) {
	eventModuleData, err := s.GetEventModuleData(userID)
	if err != nil {
		return nil, err
	}

	eventData, exists := eventModuleData.Events[eventID]
	if !exists {
		// 創建新的活動數據
		now := time.Now().Format(time.RFC3339)
		eventData = &UnifiedUserEventData{
			TotalParticipations: 0,
			ConsecutiveDays:     0,
			ClaimedRewards:      make([]UnifiedRewardRecord, 0),
			Progress:            make(map[string]interface{}),
			CreatedAt:           now,
			UpdatedAt:           now,
		}
		eventModuleData.Events[eventID] = eventData
	}

	return eventData, nil
}

// SaveEventData 保存指定活動的用戶數據
func (s *EventStorageAdapter) SaveEventData(userID, eventID string, data *UnifiedUserEventData) error {
	eventModuleData, err := s.GetEventModuleData(userID)
	if err != nil {
		return err
	}

	// 更新時間戳
	data.UpdatedAt = time.Now().Format(time.RFC3339)
	if data.CreatedAt == "" {
		data.CreatedAt = data.UpdatedAt
	}

	eventModuleData.Events[eventID] = data
	return s.SaveEventModuleData(userID, eventModuleData)
}

// GetAllEventData 獲取用戶所有活動數據
func (s *EventStorageAdapter) GetAllEventData(userID string) (map[string]*UnifiedUserEventData, error) {
	eventModuleData, err := s.GetEventModuleData(userID)
	if err != nil {
		return nil, err
	}

	return eventModuleData.Events, nil
}

// GetLimitModeRecord 獲取限制模式記錄
func (s *EventStorageAdapter) GetLimitModeRecord(userID, eventID, poolName string) (*common.LimitModeRecord, error) {
	key := eventID + ":" + poolName
	return s.storage.GetLimitModeRecord(userID, "event", key)
}

// UpdateLimitModeRecord 更新限制模式記錄
func (s *EventStorageAdapter) UpdateLimitModeRecord(userID, eventID, poolName string, record *common.LimitModeRecord) error {
	key := eventID + ":" + poolName
	return s.storage.UpdateLimitModeRecord(userID, "event", key, record)
}

// 實現 common.CommonStorage 接口的其他方法
func (s *EventStorageAdapter) GetUserData(userID string) (*common.UserData, error) {
	return s.storage.GetUserData(userID)
}

func (s *EventStorageAdapter) SaveUserData(userID string, data *common.UserData) error {
	return s.storage.SaveUserData(userID, data)
}

func (s *EventStorageAdapter) GetModuleData(userID, moduleName string, result interface{}) error {
	return s.storage.GetModuleData(userID, moduleName, result)
}

func (s *EventStorageAdapter) SetModuleData(userID, moduleName string, data interface{}) error {
	return s.storage.SetModuleData(userID, moduleName, data)
}

func (s *EventStorageAdapter) CleanupExpiredData() error {
	return s.storage.CleanupExpiredData()
}

func (s *EventStorageAdapter) Close() error {
	return s.storage.Close()
}
