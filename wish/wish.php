<?php

// action : daily、wish、query . 每日祈願, 祈願 , 查詢
// user : 用戶名
// type : 祈願的類型，包括 wish 與 query ： normal、 valentine、daily . 普通 、 情人節、每日...
// wish : 祈願次數 , 1 or 10 .

// 範例， test用戶 普通祈願 10 次 : ?action=wish&user=test&type=normal&wish=10
// test 用戶進行每日祈願 : ?action=daily&user=test or ?action=wish&user=test&type=daily&wish=1
// test用戶查詢普通祈願次數 : ?action=query&user=test&type=normal

require_once 'wish_core.php';

// 統一入口路由
$action = $_GET['action'] ?? '';
$user = $_GET['user'] ?? '';
$type = $_GET['type'] ?? 'normal';
$wish_value = (int)($_GET['wish'] ?? 0);


//處理動作
switch ($action) {
    case 'wish':
        if (!in_array($wish_value, [1, 10])) { // 驗證許願次數合法性
            display_error("invalid_wish_value");
        }
        handle_wish_action($user, $type, $wish_value);
        break;
    case 'query':
        handle_query_action($user, $type);
        break;    
    case 'daily':
        handle_daily_wish($user);
        break;    
    default:
        display_error("invalid_action");
        break;
}


// 1 =  "invalid_user_name" : 無效的用戶名
// 2 =  "invalid_wish_type": 無效的祈願類型
// 3 =  "invalid_wish_value": 無效的祈願次數
// 4 =  "invalid_action": 無效操作
// 5 = "operation_frequent": 操作頻繁
// 6 = "operation_frequent_daily": 已經進行過今天的每日祈願
// 999 =  "unknown_error": 未知錯誤
function display_error($err) {
    
    switch($err) {
        case 1: $err = "invalid_user_name"; break;
        case 2: $err = "invalid_wish_type"; break;
        case 3: $err = "invalid_wish_value"; break;
        case 4: $err = "invalid_action"; break;
        case 5: $err = "operation_frequent"; break;
        case 6: $err = "operation_frequent_daily"; break;
        case 999: $err = "unknown_error"; break;
        default: break;
    }
    switch ($err) {
        case 'invalid_user_name':
        case 'invalid_wish_type':
        case 'invalid_wish_value':
        case 'invalid_action':
        case 'operation_frequent':
        case 'operation_frequent_daily':
            echo "/err_text_wish $err"; 
            break;
        default:
            echo "/err_text_wish unknown_error"; 
            break;
    }
    exit;
}

// 祈願操作處理
function handle_wish_action($user, $type, $value) {
    if ($type === "daily") { //處理每日祈願，如果需要測試，將這個條件註釋掉以按照正常祈願的邏輯執行。
        handle_daily_wish($user);
        exit;
    }

    validate_params($user, $type);
    
    $config = get_wish_config($type);
    if (!$config) {
        display_error("invalid_wish_type");
    }
    
    init_user_dir($user, $config['dir']);
    
    $items = [];
    for ($i = 0; $i < $value; $i++) {
        $is_guarantee = handle_guarantee($user, $type);
        if ($is_guarantee) {
            $items[] = $is_guarantee;
        } else {
            $items[] = get_random_item($config['items']);
            update_wish_count($user, $type, 1);
        }
    }
    
    echo "/run_wish_$type $value ".implode(" ", $items);
}

// 查詢操作處理
function handle_query_action($user, $type) {
    validate_params($user, $type);
    
    $config = get_wish_config($type);
    if (!$config) {
        display_error("invalid_wish_type");
    }
    
    $log_path = "{$config['dir']}/$user/{$config['log_file']}";
    echo file_exists($log_path) ? (int)file_get_contents($log_path) : 0;
}

// 每日祈願處理
function handle_daily_wish($user) {
    $type = 'daily';
    validate_params($user, $type);
    
    $config = get_wish_config($type);
    if (!$config) {
        display_error("invalid_wish_type");
    }
    
    init_user_dir($user, $config['dir']);
    
    $today = date('Y-m-d');
    
    $date_log_path = "{$config['dir']}/$user/{$config['date_file']}";
    $last_time = file_exists($date_log_path) ? file_get_contents($date_log_path) : '0';
    
    if ($last_time === $today) {
        display_error("operation_frequent_daily"); // 今天已經祈願過了
    }
    file_put_contents($date_log_path,$today);
    $item = get_random_item($config['items']);
    $is_guarantee = handle_guarantee($user, $type);
    
    update_wish_count($user,$type,1);
    
    if ($item === 'potion') {
        $item = "potion ".get_random_item($config['potion']);
    } elseif ($item === 'op_equip') {
        $item = $config['guarantee_items'][array_rand($config['guarantee_items'])];
    } elseif ($is_guarantee) {
        $item = $is_guarantee;
    }
    
    echo "/give_wish_$type $item";
}

// 通用參數驗證
function validate_params($user, $type) {
    if (empty($user)) {
        display_error("invalid_user_name");
    }
    
    if (!get_wish_config($type)) {
        display_error("invalid_wish_type");
    }
}
