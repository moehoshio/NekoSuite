package wish

import (
	"fmt"
	"os"
	"path/filepath"
)

// CreateStorage 根据配置创建存储实例
func CreateStorage(config *Config) (Storage, error) {
	if config.Storage != nil {
		switch config.Storage.Type {
		case "sqlite":
			if config.Storage.DBPath == "" {
				return nil, fmt.Errorf("sqlite storage requires db_path to be set")
			}
			return NewSQLiteStorage(config.Storage.DBPath)
		case "yaml":
			dataPath := config.Storage.DataPath
			if dataPath == "" {
				dataPath = config.DataPath // 兼容旧配置
			}
			if dataPath == "" {
				return nil, fmt.Errorf("yaml storage requires data_path to be set")
			}
			return NewFileStorage(dataPath), nil
		default:
			return nil, fmt.Errorf("unsupported storage type: %s", config.Storage.Type)
		}
	} else {
		// 兼容旧配置，使用文件存储
		if config.DataPath == "" {
			return nil, fmt.Errorf("data_path is required when storage config is not provided")
		}
		return NewFileStorage(config.DataPath), nil
	}
}

// ensureDir 确保目录存在
func ensureDir(dirPath string) error {
	return os.MkdirAll(dirPath, 0755)
}

// getAbsPath 获取绝对路径
func getAbsPath(path string) (string, error) {
	if filepath.IsAbs(path) {
		return path, nil
	}
	return filepath.Abs(path)
}
