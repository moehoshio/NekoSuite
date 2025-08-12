<?php
require_once 'buy_core.php';

class LuckyPack {
    private $user;
    private $log_file;
    
    public function __construct($user) {
        $this->user = $user;
        init_user_dir($user, LUCKY_PACK_CONFIG['dir']);
        $this->log_file = LUCKY_PACK_CONFIG['dir'] . "/$user/" . LUCKY_PACK_CONFIG['log_file'];
    }

    public function canClaim() {
        if (!file_exists($this->log_file)) {
            return true;
        }
        $last_claim = trim(file_get_contents($this->log_file));
        return $last_claim !== date('Y-m-d');
    }

    public function claim() {
        if (!$this->canClaim()) {
            return "/lucky_pack_err already_claimed";
        }

        // 記錄領取時間
        file_put_contents($this->log_file, date('Y-m-d'));

        // 計算總機率並抽獎
        $total_chance = array_sum(array_column(LUCKY_PACK_CONFIG['rewards'], 'chance'));
        $roll = mt_rand(1, $total_chance);
        
        $current_sum = 0;
        foreach (LUCKY_PACK_CONFIG['rewards'] as $reward) {
            $current_sum += $reward['chance'];
            if ($roll <= $current_sum) {
                // 找到獎勵
                if ($reward['type'] === 'money') {
                    return "/lucky_pack_give money {$reward['amount']} 0";
                } else {
                    return "/lucky_pack_give item {$reward['item']} {$reward['amount']}";
                }
            }
        }

        // 不應該到達這裡，但為了安全返回最低金幣獎勵
        return "/lucky_pack_give money 5 0";
    }
}

// 處理請求
$action = $_GET['action'] ?? '';
$user = $_GET['user'] ?? '';

if ($action == 'claim' && !empty($user)) {
    $pack = new LuckyPack($user);
    echo $pack->claim();
} else {
    echo "/lucky_pack_err invalid_action";
}
