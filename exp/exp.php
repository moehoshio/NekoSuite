<?php

const USER_BASE_PATH = "/web/neko.moe/usr";
const ITEM_PATH_NAME = "exp";
const LOG_FILE = "exp.log";
const TAX_RATE = 0.08; //轉賬手續費稅率

// 經驗兌換物品
// limit類型： once、daily、total，僅一次、每日一次、總共限制次數
// cost：消耗經驗
// times：總次數限制
const ITEMS = [ 
    "CloudspringAzureBlade"=> [
        "log_file"=> "CloudspringAzureBlade.log",
        "limit"=> "once", 
        "cost"=> 300000 //消耗經驗
    ],
    "bal100" => [
        "log_file"=> "bal100.log",
        "limit"=> "daily",
        "cost"=> 10000
    ],
    "enchantedGoldenApple3" => [
        "log_file"=> "enchantedGoldenApple3.log",
        "limit"=> "total",
        "times"=> 100,
        "cost"=> 5000
    ]
];


$user = $_GET["user"] ?? '';
$action = $_GET["action"] ?? '';
$obj = $_GET["object"] ?? '';
$item = $_GET["item"] ?? ''; // 動作爲exchange時 ，兌換的物品
$outraw = $_GET["outraw"] ?? ''; // 輸出原始數值,用於適配僅需數字值的情況. 僅查詢有效
$nums = $_GET["nums"] ?? 0;

$user_file = USER_BASE_PATH ."/$user/" . LOG_FILE;
$obj_file = USER_BASE_PATH . "/$obj/" . LOG_FILE;

$item_path = USER_BASE_PATH ."/$user/" . ITEM_PATH_NAME;

validate_params($user, $action); //驗證參數

init_user_dir($user,USER_BASE_PATH); //初始化用戶目錄

switch ($action) {
    case 'save':
        if (!validateNums($nums)) {
            display_error("invalid_params");
        }
        $new = (file_exists($user_file))? file_get_contents($user_file) + $nums : $nums;
        file_put_contents($user_file, $new);
        echo "/run_exp_msg overAndOut $nums $new";
        break;
    case 'raw':
        if (!validateNums($nums)) {
            display_error("invalid_params");
        }
        $raw = (file_exists($user_file))? file_get_contents($user_file) : 0;
        if ($nums > $raw) {
            echo "/err_text_exp expNotEnough $nums $raw";
            exit;
        }
        $new = $raw - $nums;
        file_put_contents($user_file, $new);
        echo "/run_exp_msg success $nums $new";
        break;
    case 'pay':
        if ($user == $obj) {
            display_error("isSameUser");
        }
        if (!validateNums($nums)) {
            display_error("invalid_params");
        }
        init_user_dir($obj,USER_BASE_PATH);
        $raw = (file_exists($user_file))? file_get_contents($user_file) : 0;
        $objraw = (file_exists($obj_file))? file_get_contents($obj_file) : 0;
        if ($nums > $raw) {
            echo "/err_text_exp expNotEnough $nums $raw";
            exit;
        }
        $new = $raw - $nums;
        $newn = intval($nums * (1 - TAX_RATE) );
        $objnew = $objraw + $newn;
        file_put_contents($user_file, $new);
        file_put_contents($obj_file, $objnew);
        echo "/run_exp_msg successPay $obj $nums $new $newn";
        break;
    case 'info':
        $raw = (file_exists($user_file))? file_get_contents($user_file) : 0;
        if (!empty(OUTRAW)){
            if ( !empty(ITEM) && array_key_exists(ITEM, ITEMS)) {
                $config = ITEMS[ITEM];
                $log_path = "$item_path/{$config['log_file']}";
                $log = (file_exists($log_path))? file_get_contents($log_path) : 0;
                die($log);
            }
            die($raw);
        }
            
        echo "/run_exp_msg info $raw";
        break;
    case 'exchange':
        if (!array_key_exists(ITEM, ITEMS)) {
            display_error("invalid_params");
        }

        $config = ITEMS[ITEM];
        $log_path = "$item_path/{$config['log_file']}";

        $raw = (file_exists($user_file))? file_get_contents($user_file) : 0;

        if ($raw < $config['cost']) {
            display_error("expNotEnough");
        }

        switch ($config['limit']) {
            case 'once':
                if (file_exists($log_path)) {
                    display_error("alreadyUsed");
                }
                file_put_contents($log_path, date('Y-m-d H:i:s'));
                break;
            case 'daily':
                $log = (file_exists($log_path))? file_get_contents($log_path) : 0;

                if (date('Y-m-d') == $log) 
                    display_error("todayAlreadyUsed");

                file_put_contents($log_path, date('Y-m-d'));
                break;
            case 'total':
                $log = (file_exists($log_path))? file_get_contents($log_path) : 0;
                if ($log >= $config['times']) {
                    display_error("totalLimit");
                }
                file_put_contents($log_path, $log + 1);
                break;
            default:
                display_error("invalid_params");
                break;
        }
        $new = $raw - $config['cost'];
        file_put_contents($user_file, $new);
        echo "/exp_exchange " . ITEM ." {$config['cost']} $new";    
        break;
    default:
        display_error("invalid_action");
        break;
}
exit;


function validateNums($Nums) {
    return preg_match("/^[1-9][0-9]*$/", $Nums);
}

function init_user_dir($user, $dir) {
    if (!file_exists("$dir/$user")) {
        mkdir("$dir/$user", 0777, true);
    }
}

function validate_params($user, $action) {
    if (empty($user))
        display_error("invalid_user");
    if (empty($action))
        display_error("invalid_action");
}

function display_error($err) {
    switch ($err) {
        case 'invalid_user':
        case 'invalid_params':
        case 'invalid_action':
        case 'expNotEnough':
        case 'isSameUser':
        case 'alreadyUsed':
        case 'todayAlreadyUsed':
        case 'totalLimit':
            echo "/err_text_exp $err";
            break;
        default:
            echo "/err_text_exp unknown_error";
            break;
    }
    exit;
}