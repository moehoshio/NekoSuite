package common

import (
	"fmt"
	"os"
	"path/filepath"
	"time"

	"gopkg.in/yaml.v3"
)

// YAMLStorage YAML 文件存儲實現
type YAMLStorage struct {
	dataDir string
}

// NewYAMLStorage 創建 YAML 存儲實例
func NewYAMLStorage(dataDir string) (*YAMLStorage, error) {
	// 確保數據目錄存在
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		return nil, fmt.Errorf("創建數據目錄失敗: %w", err)
	}

	return &YAMLStorage{
		dataDir: dataDir,
	}, nil
}

// getUserDataPath 獲取用戶數據文件路徑
func (s *YAMLStorage) getUserDataPath(userID string) string {
	return filepath.Join(s.dataDir, userID+".yml")
}

// GetUserData 獲取用戶數據
func (s *YAMLStorage) GetUserData(userID string) (*UserData, error) {
	filePath := s.getUserDataPath(userID)

	// 如果文件不存在，創建新的用戶數據
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		now := time.Now().Format(time.RFC3339)
		userData := &UserData{
			CreatedAt:  now,
			UpdatedAt:  now,
			ModuleData: make(map[string]interface{}),
		}
		return userData, nil
	}

	// 讀取文件
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("讀取用戶數據文件失敗: %w", err)
	}

	// 解析 YAML
	var userData UserData
	if err := yaml.Unmarshal(data, &userData); err != nil {
		return nil, fmt.Errorf("解析用戶數據失敗: %w", err)
	}

	// 確保 ModuleData 已初始化
	if userData.ModuleData == nil {
		userData.ModuleData = make(map[string]interface{})
	}

	return &userData, nil
}

// SaveUserData 保存用戶數據
func (s *YAMLStorage) SaveUserData(userID string, data *UserData) error {
	// 更新時間戳
	data.UpdatedAt = time.Now().Format(time.RFC3339)
	if data.CreatedAt == "" {
		data.CreatedAt = data.UpdatedAt
	}

	// 序列化為 YAML
	yamlData, err := yaml.Marshal(data)
	if err != nil {
		return fmt.Errorf("序列化用戶數據失敗: %w", err)
	}

	// 寫入文件
	filePath := s.getUserDataPath(userID)
	if err := os.WriteFile(filePath, yamlData, 0644); err != nil {
		return fmt.Errorf("寫入用戶數據文件失敗: %w", err)
	}

	return nil
}

// GetModuleData 獲取模塊數據
func (s *YAMLStorage) GetModuleData(userID, moduleName string, result interface{}) error {
	userData, err := s.GetUserData(userID)
	if err != nil {
		return err
	}

	moduleData, exists := userData.ModuleData[moduleName]
	if !exists {
		// 如果模塊數據不存在，result 保持零值
		return nil
	}

	// 將數據轉換為目標類型
	yamlBytes, err := yaml.Marshal(moduleData)
	if err != nil {
		return fmt.Errorf("序列化模塊數據失敗: %w", err)
	}

	if err := yaml.Unmarshal(yamlBytes, result); err != nil {
		return fmt.Errorf("解析模塊數據失敗: %w", err)
	}

	return nil
}

// SetModuleData 設置模塊數據
func (s *YAMLStorage) SetModuleData(userID, moduleName string, data interface{}) error {
	userData, err := s.GetUserData(userID)
	if err != nil {
		return err
	}

	userData.ModuleData[moduleName] = data

	return s.SaveUserData(userID, userData)
}

// GetLimitModeRecord 獲取限制模式記錄
func (s *YAMLStorage) GetLimitModeRecord(userID, moduleName, poolName string) (*LimitModeRecord, error) {
	userData, err := s.GetUserData(userID)
	if err != nil {
		return nil, err
	}

	// 獲取模塊數據
	moduleDataRaw, exists := userData.ModuleData[moduleName]
	if !exists {
		return nil, nil // 沒有記錄
	}

	moduleData, ok := moduleDataRaw.(map[string]interface{})
	if !ok {
		return nil, nil
	}

	// 獲取限制模式記錄
	limitRecordsRaw, exists := moduleData["limit_mode_records"]
	if !exists {
		return nil, nil
	}

	limitRecords, ok := limitRecordsRaw.(map[string]interface{})
	if !ok {
		return nil, nil
	}

	recordRaw, exists := limitRecords[poolName]
	if !exists {
		return nil, nil
	}

	// 轉換為 LimitModeRecord
	yamlBytes, err := yaml.Marshal(recordRaw)
	if err != nil {
		return nil, fmt.Errorf("序列化限制模式記錄失敗: %w", err)
	}

	var record LimitModeRecord
	if err := yaml.Unmarshal(yamlBytes, &record); err != nil {
		return nil, fmt.Errorf("解析限制模式記錄失敗: %w", err)
	}

	return &record, nil
}

// UpdateLimitModeRecord 更新限制模式記錄
func (s *YAMLStorage) UpdateLimitModeRecord(userID, moduleName, poolName string, record *LimitModeRecord) error {
	userData, err := s.GetUserData(userID)
	if err != nil {
		return err
	}

	// 確保模塊數據存在
	if userData.ModuleData[moduleName] == nil {
		userData.ModuleData[moduleName] = make(map[string]interface{})
	}

	moduleDataRaw := userData.ModuleData[moduleName]
	moduleData, ok := moduleDataRaw.(map[string]interface{})
	if !ok {
		moduleData = make(map[string]interface{})
		userData.ModuleData[moduleName] = moduleData
	}

	// 確保限制模式記錄結構存在
	if moduleData["limit_mode_records"] == nil {
		moduleData["limit_mode_records"] = make(map[string]interface{})
	}

	limitRecordsRaw := moduleData["limit_mode_records"]
	limitRecords, ok := limitRecordsRaw.(map[string]interface{})
	if !ok {
		limitRecords = make(map[string]interface{})
		moduleData["limit_mode_records"] = limitRecords
	}

	// 更新記錄
	limitRecords[poolName] = record

	return s.SaveUserData(userID, userData)
}

// CleanupExpiredData 清理過期數據
func (s *YAMLStorage) CleanupExpiredData() error {
	// TODO: 實現過期數據清理邏輯
	return nil
}

// Close 關閉存儲
func (s *YAMLStorage) Close() error {
	// YAML 文件存儲無需關閉操作
	return nil
}
