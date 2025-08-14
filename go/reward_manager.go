package common

import (
	"fmt"
	"strconv"
	"strings"
)

// RewardManager 獎勵管理器
type RewardManager struct{}

// NewRewardManager 創建獎勵管理器
func NewRewardManager() *RewardManager {
	return &RewardManager{}
}

// BuildRewardCommand 構建獎勵發放命令
// 根據獎勵配置生成對應的 MyCmd 命令
func (rm *RewardManager) BuildRewardCommand(rewards []RewardConfig, handlerPrefix string) []string {
	if len(rewards) == 0 {
		return []string{}
	}

	commands := make([]string, 0)

	for i, reward := range rewards {
		cmd := rm.buildSingleRewardCommand(reward, handlerPrefix, i+1)
		if cmd != "" {
			commands = append(commands, cmd)
		}
	}

	return commands
}

// buildSingleRewardCommand 構建單個獎勵命令
func (rm *RewardManager) buildSingleRewardCommand(reward RewardConfig, handlerPrefix string, index int) string {
	switch reward.Type {
	case "balance":
		// 金錢獎勵: /handle_event_result balance 100
		if amount, ok := reward.Value.(float64); ok {
			return fmt.Sprintf("/%s balance %.0f", handlerPrefix, amount)
		} else if amountStr, ok := reward.Value.(string); ok {
			return fmt.Sprintf("/%s balance %s", handlerPrefix, amountStr)
		}

	case "exp":
		// 經驗獎勵: /handle_event_result exp 50
		if amount, ok := reward.Value.(float64); ok {
			return fmt.Sprintf("/%s exp %.0f", handlerPrefix, amount)
		} else if amountStr, ok := reward.Value.(string); ok {
			return fmt.Sprintf("/%s exp %s", handlerPrefix, amountStr)
		}

	case "item":
		// 物品獎勵: /handle_event_result item minecraft:diamond 1
		if itemStr, ok := reward.Value.(string); ok {
			amount := reward.Amount
			if amount <= 0 {
				amount = 1
			}
			return fmt.Sprintf("/%s item %s %d", handlerPrefix, itemStr, amount)
		}

	case "command":
		// 自定義命令: /handle_event_result command "minecraft:give $player minecraft:diamond 1"
		if cmdStr, ok := reward.Value.(string); ok {
			return fmt.Sprintf("/%s command \"%s\"", handlerPrefix, cmdStr)
		}

	default:
		// 未知類型，嘗試作為字符串處理
		if valueStr, ok := reward.Value.(string); ok {
			return fmt.Sprintf("/%s unknown %s %s", handlerPrefix, reward.Type, valueStr)
		}
	}

	return ""
}

// ParseRewardFromString 從字符串解析獎勵配置
// 用於從 YAML 配置中解析複雜的獎勵設置
func (rm *RewardManager) ParseRewardFromString(rewardStr string) (RewardConfig, error) {
	parts := strings.Fields(rewardStr)
	if len(parts) < 2 {
		return RewardConfig{}, fmt.Errorf("invalid reward format: %s", rewardStr)
	}

	rewardType := parts[0]

	switch rewardType {
	case "balance", "exp":
		if len(parts) != 2 {
			return RewardConfig{}, fmt.Errorf("invalid %s reward format: %s", rewardType, rewardStr)
		}

		amount, err := strconv.ParseFloat(parts[1], 64)
		if err != nil {
			return RewardConfig{}, fmt.Errorf("invalid %s amount: %s", rewardType, parts[1])
		}

		return RewardConfig{
			Type:  rewardType,
			Value: amount,
		}, nil

	case "item":
		if len(parts) < 2 || len(parts) > 3 {
			return RewardConfig{}, fmt.Errorf("invalid item reward format: %s", rewardStr)
		}

		itemName := parts[1]
		amount := 1

		if len(parts) == 3 {
			var err error
			amount, err = strconv.Atoi(parts[2])
			if err != nil {
				return RewardConfig{}, fmt.Errorf("invalid item amount: %s", parts[2])
			}
		}

		return RewardConfig{
			Type:   "item",
			Value:  itemName,
			Amount: amount,
		}, nil

	case "command":
		// 對於命令類型，將除第一個部分外的所有部分合併為命令
		if len(parts) < 2 {
			return RewardConfig{}, fmt.Errorf("invalid command reward format: %s", rewardStr)
		}

		command := strings.Join(parts[1:], " ")
		return RewardConfig{
			Type:  "command",
			Value: command,
		}, nil

	default:
		return RewardConfig{}, fmt.Errorf("unknown reward type: %s", rewardType)
	}
}

// FormatRewardDescription 格式化獎勵描述（用於顯示）
func (rm *RewardManager) FormatRewardDescription(reward RewardConfig) string {
	switch reward.Type {
	case "balance":
		if amount, ok := reward.Value.(float64); ok {
			return fmt.Sprintf("金錢 x%.0f", amount)
		} else if amountStr, ok := reward.Value.(string); ok {
			return fmt.Sprintf("金錢 x%s", amountStr)
		}

	case "exp":
		if amount, ok := reward.Value.(float64); ok {
			return fmt.Sprintf("經驗 x%.0f", amount)
		} else if amountStr, ok := reward.Value.(string); ok {
			return fmt.Sprintf("經驗 x%s", amountStr)
		}

	case "item":
		if itemStr, ok := reward.Value.(string); ok {
			amount := reward.Amount
			if amount <= 0 {
				amount = 1
			}
			return fmt.Sprintf("%s x%d", itemStr, amount)
		}

	case "command":
		return "特殊獎勵"

	default:
		return fmt.Sprintf("未知獎勵: %s", reward.Type)
	}

	return "無效獎勵"
}

// CalculateRewardValue 計算獎勵的數值（用於統計和比較）
func (rm *RewardManager) CalculateRewardValue(reward RewardConfig) float64 {
	switch reward.Type {
	case "balance", "exp":
		if amount, ok := reward.Value.(float64); ok {
			return amount
		} else if amountStr, ok := reward.Value.(string); ok {
			if val, err := strconv.ParseFloat(amountStr, 64); err == nil {
				return val
			}
		}

	case "item":
		// 對於物品，可以設定一個基礎價值
		amount := float64(reward.Amount)
		if amount <= 0 {
			amount = 1
		}
		return amount * 10 // 假設每個物品價值10

	case "command":
		return 100 // 自定義命令給予固定價值
	}

	return 0
}
