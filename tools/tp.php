<?php

const USER_BASE_PATH = "/web/neko.moe/usr";

$action = $_GET['action'] ?? '';
$user = $_GET['user'] ?? '';
$target = $_GET['target'] ?? '';
$x = $_GET['x'] ?? '';
$y = $_GET['y'] ?? '';
$z = $_GET['z'] ?? '';
$state = $_GET['state'] ?? '';

define("USER_PATH", USER_BASE_PATH . "/$user");

switch ($action) {
    case 'set_state':
        tp_set_state($user, $state);
        break;
    case 'tp':
        handle_tp($user, $target, $x, $y, $z);
        break;
    case 'player_config':
        $deny_others = $_GET['deny_others'] ?? ''; // "yes" or "no"
        tp_player_config($user, $deny_others);
        break;
    case 'admin_set_config':
        $admin_target = $_GET['admin_target'] ?? '';
        $canUse = $_GET['canUse'] ?? '';      // "yes" or "no"
        $canBeTp = $_GET['canBeTp'] ?? '';    // "yes" or "no"
        tp_admin_config($admin_target, $canUse, $canBeTp);
        break;
    default:
        display_error("invalid_action");
        break;
}

function tp_set_state($user, $state) {
    if (!in_array($state, ['allow', 'deny'])) {
        display_error("invalid_state");
    }

    $state_path = USER_PATH . "/tp_state.txt";
    file_put_contents($state_path, $state);
    echo "/tp_text set_okay_$state";
}

function handle_tp($user, $target, $x, $y, $z) {
    if (empty($user)) {
        display_error("invalid_params");
    }

    $user_state_path = USER_PATH . "/tp_state.txt";
    if (file_exists($user_state_path) && trim(file_get_contents($user_state_path)) == 'deny') {
        display_error("tp_denied");
    }

    if ($user === $target) {
        display_error("tp_self");
    }

    if (is_coordinates($target, $x, $y)) {
        echo "/tp_cost tpxyz $target $x $y";
    } elseif (is_selector($target) && is_coordinates($x, $y, $z)) {
        echo "/tp_cost tpxyz_msg $target $x $y $z";
    } elseif (!empty($target) && is_empty_param($x) && is_empty_param($y) && is_empty_param($z)) {
        $target_state_path = USER_BASE_PATH . "/$target/tp_state.txt";
        if (file_exists($target_state_path) && trim(file_get_contents($target_state_path)) == 'deny') {
            display_error("tp_denied");
        }
        echo "/tp_cost tp_player $user $target";
    } else {
        display_error("invalid_target");
    }
}

function tp_player_config($user, $deny_others) {
    if (empty($user)) {
        display_error("invalid_params");
    }
    $val = ($deny_others === 'yes') ? 'allow' : 'deny';
    file_put_contents(USER_PATH . "/tp_state.txt", $val);
    echo "/tp_text set_okay_$val";
}

function tp_admin_config($admin_target, $canUse, $canBeTp) {
    if (empty($admin_target)) {
        display_error("invalid_params");
    }
    $target_path = USER_BASE_PATH . "/$admin_target";
    if (!is_dir($target_path)) {
        display_error("invalid_params");
    }
    // 設定「可否使用傳送」
    file_put_contents("$target_path/tp_can_use.txt", ($canUse === 'yes' ? '1' : '0'));
    // 設定「可否被傳送」
    file_put_contents("$target_path/tp_can_be_tp.txt", ($canBeTp === 'yes' ? '1' : '0'));
    echo "/tp_text set_okay";
}

function is_coordinates($x, $y, $z) {
    return (is_numeric($x) || preg_match('/^[~\-]?\d+(\.\d+)?$/', $x)) &&
           (is_numeric($y) || preg_match('/^[~\-]?\d+(\.\d+)?$/', $y)) &&
           (is_numeric($z) || preg_match('/^[~\-]?\d+(\.\d+)?$/', $z));
}

function is_selector($target) {
    // 檢查target是否為選擇符
    return in_array($target, ['@s', '@p', '@r', '@a', '@e']);
}

function is_empty_param($param) {
    return empty($param) || strpos($param, '$') !== false;
}

function display_error($err) {
    switch ($err) {
        case 'invalid_params':
        case 'invalid_state':
        case 'tp_denied':
        case 'invalid_action':
        case 'invalid_target':
        case 'tp_self':
            echo "/err_text_tp $err";
            break;
        default:
            echo "/err_text_tp unknown_error";
            break;
    }
    exit;
}
?>
