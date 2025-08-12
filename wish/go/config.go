package wish

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// Config config structure
type Config struct {
	DataPath string                    `yaml:"data_path"`
	Storage  *StorageConfig            `yaml:"storage"`
	History  *HistoryConfig            `yaml:"history,omitempty"`
	Tickets  []TicketConfig            `yaml:"tickets,omitempty"`
	Pools    map[string]WishPoolConfig `yaml:"pools"`
}

// StorageConfig storage configuration
type StorageConfig struct {
	Type     string `yaml:"type"`
	DataPath string `yaml:"data_path"`
	DBPath   string `yaml:"db_path"`
}

// TicketConfig ticket configuration
type TicketConfig struct {
	ID              string   `yaml:"id"`
	ApplicablePools []string `yaml:"applicable_pools"`
	DeductCount     int      `yaml:"deduct_count"`
	DeductMode      string   `yaml:"deduct_mode"`
}

// HistoryConfig history configuration
type HistoryConfig struct {
	MaxSize    int    `yaml:"max_size"`
	Expiration string `yaml:"expiration"`
}

// WishPoolConfig wish pool configuration
type WishPoolConfig struct {
	Dir            string                    `yaml:"dir"`
	MaxCount       int                       `yaml:"max_count"`
	CountsName     string                    `yaml:"counts_name,omitempty"`
	LimitModes     *LimitModes               `yaml:"limit_modes,omitempty"`
	Duration       *Duration                 `yaml:"duration,omitempty"`
	Cost           map[string]int            `yaml:"cost,omitempty"`
	AutoCost       bool                      `yaml:"auto_cost,omitempty"`
	Items          map[string]WishItemConfig `yaml:"items"`
	GuaranteeItems map[string]WishItemConfig `yaml:"guarantee_items,omitempty"`
}

// LimitModes limit modes configuration
type LimitModes struct {
	Count          int    `yaml:"count"`
	Time           string `yaml:"time"`
	RefreshAtTime  string `yaml:"refresh_at_time"`
	RefreshAtWeek  int    `yaml:"refresh_at_week"`
	RefreshAtMonth int    `yaml:"refresh_at_month"`
}

// Duration duration configuration
type Duration struct {
	StartDate string `yaml:"startDate"`
	EndDate   string `yaml:"endDate"`
}

// WishItemConfig wish item configuration
type WishItemConfig struct {
	Probability float64            `yaml:"-"`
	SubList     map[string]float64 `yaml:"subList,omitempty"`
}

// UnmarshalYAML custom YAML unmarshaling
func (w *WishItemConfig) UnmarshalYAML(value *yaml.Node) error {
	if value.Kind == yaml.ScalarNode {
		var prob float64
		if err := value.Decode(&prob); err != nil {
			return err
		}
		w.Probability = prob
		return nil
	}

	if value.Kind == yaml.MappingNode {
		type CompoundItem struct {
			Probability float64            `yaml:"probability"`
			SubList     map[string]float64 `yaml:"subList"`
		}

		var compound CompoundItem
		if err := value.Decode(&compound); err != nil {
			return err
		}

		w.Probability = compound.Probability
		w.SubList = compound.SubList
		return nil
	}

	return fmt.Errorf("invalid item config format")
}

// MarshalYAML custom YAML marshaling
func (w WishItemConfig) MarshalYAML() (interface{}, error) {
	if len(w.SubList) > 0 {
		return map[string]interface{}{
			"probability": w.Probability,
			"subList":     w.SubList,
		}, nil
	}
	return w.Probability, nil
}

// IsCompound check if item is compound type
func (w *WishItemConfig) IsCompound() bool {
	return len(w.SubList) > 0
}

// LoadConfig load configuration file
func LoadConfig(filename string) (*Config, error) {
	data, err := os.ReadFile(filename)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	var config Config
	if err := yaml.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("failed to parse config file: %w", err)
	}

	return &config, nil
}

// GetPoolConfig get pool configuration
func (c *Config) GetPoolConfig(poolType string) (WishPoolConfig, bool) {
	config, exists := c.Pools[poolType]
	return config, exists
}

// ValidatePoolType validate pool type
func (c *Config) ValidatePoolType(poolType string) bool {
	_, exists := c.Pools[poolType]
	return exists
}
