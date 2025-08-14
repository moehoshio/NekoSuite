package event

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"

	common "neko-suite/go"
)

// EventConfig 活動配置
type EventConfig struct {
	// 存儲配置（統一）
	Storage common.StorageConfig `yaml:"storage"`

	// 活動列表
	Events map[string]*EventItemConfig `yaml:"events"`
}

// EventItemConfig 單個活動配置
type EventItemConfig struct {
	// 活動基本信息
	Name        string `yaml:"name"`        // 活動名稱
	Description string `yaml:"description"` // 活動描述

	// 活動時間配置
	Duration *common.DurationConfig `yaml:"duration,omitempty"`

	// 限制模式配置
	LimitModes *common.LimitModeConfig `yaml:"limit_modes,omitempty"`

	// 活動類型
	Type string `yaml:"type"` // daily_reward, check_in, task_complete, holiday

	// 獎勵配置
	Rewards []common.RewardConfig `yaml:"rewards"`

	// 是否自動發放獎勵
	AutoClaim bool `yaml:"auto_claim"`

	// 活動參數（根據不同活動類型有不同的參數）
	Parameters map[string]interface{} `yaml:"parameters,omitempty"`

	// 是否啟用
	Enabled bool `yaml:"enabled"`
}

// LoadConfig 加載配置文件
func LoadConfig(configPath string) (*EventConfig, error) {
	data, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	var config EventConfig
	if err := yaml.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}

	// 驗證配置
	if err := validateConfig(&config); err != nil {
		return nil, fmt.Errorf("invalid config: %w", err)
	}

	return &config, nil
}

// validateConfig 驗證配置有效性
func validateConfig(config *EventConfig) error {
	// 驗證存儲配置
	if config.Storage.Type == "" {
		return fmt.Errorf("storage type is required")
	}

	if config.Storage.Type != "yaml" && config.Storage.Type != "sqlite" {
		return fmt.Errorf("unsupported storage type: %s", config.Storage.Type)
	}

	if config.Storage.Type == "yaml" && config.Storage.DataDir == "" {
		return fmt.Errorf("data_dir is required for yaml storage")
	}

	if config.Storage.Type == "sqlite" {
		return fmt.Errorf("sqlite storage is not supported yet")
	}

	// 驗證活動配置
	for eventID, eventConfig := range config.Events {
		if eventConfig.Name == "" {
			return fmt.Errorf("event %s: name is required", eventID)
		}

		if eventConfig.Type == "" {
			return fmt.Errorf("event %s: type is required", eventID)
		}

		// 驗證活動類型
		validTypes := []string{"daily_reward", "check_in", "task_complete", "holiday", "custom"}
		isValidType := false
		for _, validType := range validTypes {
			if eventConfig.Type == validType {
				isValidType = true
				break
			}
		}
		if !isValidType {
			return fmt.Errorf("event %s: unsupported type %s", eventID, eventConfig.Type)
		}

		// 驗證獎勵配置
		if len(eventConfig.Rewards) == 0 {
			return fmt.Errorf("event %s: at least one reward is required", eventID)
		}

		for i, reward := range eventConfig.Rewards {
			if reward.Type == "" {
				return fmt.Errorf("event %s: reward %d type is required", eventID, i)
			}
			if reward.Value == nil {
				return fmt.Errorf("event %s: reward %d value is required", eventID, i)
			}
		}
	}

	return nil
}
