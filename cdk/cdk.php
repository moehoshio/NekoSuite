<?php
require_once 'cdk_core.php';

$action = $_GET['action'] ?? '';
$user = $_GET['user'] ?? '';
$cdk = $_GET['cdk'] ?? '';

define("USER_PATH",USER_BASE_PATH . "/$user");


switch ($action) {
    case 'exchange':
        handle_cdk_redeem($user, $cdk);
        break;
    default:
        display_error("invalid_action");
        break;
}

function handle_cdk_redeem($user, $cdk) {
    if (empty($user) || empty($cdk)) {
        display_error("invalid_params");
    }

    $cdk_type = get_cdk_type($cdk);
    if (!$cdk_type) {
        display_error("invalid_cdk");
    }

    $config = get_cdk_config($cdk_type, $cdk);
    if (!$config) {
        display_error("invalid_cdk");
    }

    init_cdk_dir(USER_PATH);
    init_cdk_dir(PUBLIC_CDK_PATH);

    switch ($cdk_type) {
        case 'onec_time':
            handle_onec_time_cdk($user, $cdk, $config);
            break;
        case 'user_once':
            handle_user_once_cdk($cdk, $config);
            break;
        case 'total_limit':
            handle_total_limit_cdk($cdk, $config);
            break;
        default:
            display_error("invalid_cdk_type");
            break;
    }
}
// $user : 記錄使用cdk的使用者名稱
function handle_onec_time_cdk($user, $cdk, $config) {
    $log_path = PUBLIC_CDK_PATH . "/$cdk.log";
    if (file_exists($log_path)) {
        display_error("cdk_used");
    }

    if (strtotime($config['expiry']) < time()) {
        display_error("cdk_expired");
    }

    file_put_contents($log_path, "$user " . date('Y-m-d H:i:s'));
    echo "/give_cdk_reward {$config['reward']}";
}

function handle_user_once_cdk($cdk, $config) {
    $log_path = USER_PATH . "/cdk_user_once.log";
    $used_cdks = file_exists($log_path) ? file($log_path, FILE_IGNORE_NEW_LINES) : [];
    if (in_array($cdk, $used_cdks)) {
        display_error("cdk_used");
    }

    if (strtotime($config['expiry']) < time()) {
        display_error("cdk_expired");
    }

    file_put_contents($log_path, $cdk . PHP_EOL, FILE_APPEND);
    echo "/give_cdk_reward {$config['reward']}";
}

function handle_total_limit_cdk($cdk, $config) {
    //用戶一次
    $user_log_path = USER_PATH . "/cdk_user_once.log";
    $used_cdks = file_exists($user_log_path) ? file($user_log_path, FILE_IGNORE_NEW_LINES) : [];
    if (in_array($cdk, $used_cdks)) {
        display_error("cdk_used");
    }
    if (strtotime($config['expiry']) < time()) {
        display_error("cdk_expired");
    }

    //檢查公共使用次數
    $cdk_path = PUBLIC_CDK_PATH . "/$cdk.log";
    $public_used_count = file_exists($cdk_path) ? (int)file_get_contents($cdk_path) : 0;
    if ($public_used_count >= $config['limit']) {
        display_error("cdk_limit_reached");
    }

    file_put_contents($cdk_path, $public_used_count + 1);
    file_put_contents($user_log_path, $cdk . PHP_EOL, FILE_APPEND);

    echo "/give_cdk_reward {$config['reward']}";
}

function get_cdk_type($cdk) {
    foreach (CDK_CONFIG as $type => $cdks) {
        if (array_key_exists($cdk, $cdks)) {
            return $type;
        }
    }
    return null;
}

function display_error($err) {
    switch ($err) {
        case 'invalid_params':
        case 'invalid_cdk':
        case 'cdk_expired':
        case 'cdk_used':
        case 'cdk_limit_reached':
        case 'invalid_action':
        case 'invalid_cdk_type':
            echo "/err_text_cdk $err";
            break;
        default:
            echo "/err_text_cdk unknown_error";
            break;
    }
    exit;
}
