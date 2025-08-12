<?php

// buy : 購買或續費
// query : 查詢（顯示給用戶的）
// check_expiry : 檢查到期（通常爲系統定時調用）
$action = $_GET['action'] ?? '';
$user = $_GET['user'] ?? '';
$type = $_GET['type'] ?? '';
$level = (int)($_GET['level'] ?? 0);
$value = (int)($_GET['value'] ?? 0);

require_once 'buy_core.php';

switch ($action) {
    case 'buy':
        handle_buy_action($user, $type, $level, $value);
        break;
    case 'query':
        handle_query_action($user, $type);
        break;
    case 'check_expiry':
        handle_check_expiry_action($user);
        break;
    default:
        display_error("invalid_action");
        break;
}

// 錯誤處理
function display_error($err) {
    switch ($err) {
        case 'invalid_user_name':
        case 'invalid_type':
        case 'invalid_value':
        case 'invalid_action':
        case 'invalid_level':
        case 'the_already_have':
        case 'level_cannot_downgrade':
            die("/buy_err_text $err");
        default:
            die("/buy_err_text unknown_error");
    }
}

// 處理購買操作
function handle_buy_action($user, $type, $level, $value) {
    if (in_array($type, NON_SUBSCRIPTION_TYPES)) { //處理非訂閱制的購買
        handle_buy_non_subscription_action($user, $type, $level);
        return;
    }
    
    validate_params($user, $type);

    if ($level < 1) {
        display_error("invalid_level");
    }
    if ($value <= 0) {
        display_error("invalid_value");
    }
    
    $config = get_buy_config($type);
    if (!$config) {
        display_error("invalid_type");
    }

    init_user_dir($user, $config['dir']);

    $log_path = "{$config['dir']}/$user/{$config['log_file']}";
    $current_data = file_exists($log_path) ? json_decode(file_get_contents($log_path), true) : ['level' => 0, 'expiry' => date('Y-m-d')];
    $current_level = $current_data['level'];
    $current_cost = $current_level > 0 ? $config['level'][$current_level] : 0;
    $new_cost = $config['level'][$level];

    if ($level != $current_level) {
        // 升級或降級，計算剩餘時間的等價時長
        $remaining_days = (strtotime($current_data['expiry']) - time()) / (60 * 60 * 24);
        $remaining_value = $remaining_days * $current_cost;
        $equivalent_days = floor($remaining_value / $new_cost);
        $expiry_date = date('Y-m-d', strtotime("+$value days +$equivalent_days days"));
    } else {
        // 續費
        $expiry_date = date('Y-m-d', strtotime("+$value days", strtotime($current_data['expiry'])));
    }

    $new_data = ['level' => $level, 'expiry' => $expiry_date];
    file_put_contents($log_path, json_encode($new_data));

    echo "/buy_run_$type {$type}_{$level} $expiry_date";
}

// 檢查到期並指派權限
function handle_check_expiry_action($user) {
    if (empty($user)) {
        display_error("invalid_user_name");
    }

    $results = [];

    foreach (NEED_CHECK_EXPIRY_TYPES as $t) {
        $config = get_buy_config($t);
        if (!$config) {
            // 找不到設定，回傳none
            $results[$t] = 'none';
            continue;
        }
        $log_path = "{$config['dir']}/$user/{$config['log_file']}";
        if (!file_exists($log_path)) {
            // 沒有購買紀錄
            $results[$t] = 'none';
            continue;
        }
        $data = json_decode(file_get_contents($log_path), true);
        // 若是訂閱型，可能有 expiry
        if (!in_array($t, NON_SUBSCRIPTION_TYPES)) {
            if (strtotime($data['expiry']) < time()) {
                // 已過期
                $results[$t] = 'none';
                continue;
            }
        }
        // 組合成相應的權限參數
        $lvl = $data['level'];
        if ($t === 'vip') {
            $results[$t] = "vip_{$lvl}";
        } elseif ($t === 'mcd') {
            $results[$t] = "mcd_{$lvl}";
        } elseif ($t === 'bag') {
            $results[$t] = "bag_{$lvl}";
        } else {
            $results[$t] = 'none';
        }
    }

    echo "/buy_reassign_permission {$results['vip']} {$results['mcd']} {$results['bag']}";
}

// 處理查詢操作
// $type 可以是 'all' 或是配置項key
function handle_query_action($user, $type) {
    if (empty($user)) {
        display_error("invalid_user_name");
    }

    if ($type === 'all') {
        echo "/buy_run_msg all : ";
        foreach (['vip','mcd','bag'] as $t) {
            $cfg = get_buy_config($t);
            if (!$cfg) {
                echo "&d$t &cnot_found &7, ";
                continue;
            }
            $log_path = "{$cfg['dir']}/$user/{$cfg['log_file']}";
            if (!file_exists($log_path)) {
                echo "&d$t &cnot_found &7, ";
                continue;
            }
            $data = json_decode(file_get_contents($log_path), true);

            $timeColor = (strtotime($data['expiry']) < time())? "&c" : "&a"; //過期則顯示紅色   
            
            echo "&b$t &6{$data['level']} $timeColor{$data['expiry']} &7, ";
        }
        return;
    }

    validate_params($user, $type);
    $config = get_buy_config($type);
    $log_path = "{$config['dir']}/$user/{$config['log_file']}";
    if (file_exists($log_path)) {
        $data = json_decode(file_get_contents($log_path), true);
        echo "/buy_run_msg $type {$type}_{$data['level']} {$data['expiry']}";
    } else {
        echo "/buy_run_msg not_found";
    }
}


// 處理購買非訂閱制類型的操作
function handle_buy_non_subscription_action($user, $type, $level) {
    validate_params($user, $type);

    $config = get_buy_config($type);
    if (!$config) {
        display_error("invalid_type");
    }

    if (!array_key_exists($level, $config['level'])) {
        display_error("invalid_level");
    }

    init_user_dir($user, $config['dir']);

    $log_path = "{$config['dir']}/$user/{$config['log_file']}";
    $current_data = file_exists($log_path) ? json_decode(file_get_contents($log_path), true) : ['level' => 0];
    $current_level = $current_data['level'];
    $refund = $current_level > 0 ? $config['level'][$current_level] : 0;

    if ($level <= $current_level) {
        display_error(($level < $current_level)? "level_cannot_downgrade" : "the_already_have");
    }

    $new_data = ['level' => $level];
    file_put_contents($log_path, json_encode($new_data));

    echo "/buy_run_$type {$type}_{$level} $refund";
}



// 通用參數驗證
function validate_params($user, $type) {
    if (empty($user)) {
        display_error("invalid_user_name");
    }

    if (!get_buy_config($type)) {
        display_error("invalid_type");
    }
}
