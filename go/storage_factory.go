package common

import (
	"fmt"
)

// StorageType 存儲類型
type StorageType string

const (
	StorageTypeYAML StorageType = "yaml"
	// 未來可以添加其他存儲類型，如 SQLite, MySQL 等
)

// StorageConfig 存儲配置
type StorageConfig struct {
	Type    StorageType `yaml:"type" json:"type"`
	DataDir string      `yaml:"data_dir" json:"data_dir"`
	// 其他存儲特定配置可以放在這裡
}

// NewStorage 創建存儲實例
func NewStorage(config StorageConfig) (CommonStorage, error) {
	switch config.Type {
	case StorageTypeYAML:
		return NewYAMLStorage(config.DataDir)
	default:
		return nil, fmt.Errorf("不支持的存儲類型: %s", config.Type)
	}
}
