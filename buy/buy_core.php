<?php
// 核心配置
const BASE_PATH = "/web/neko.moe/usr";
const NON_SUBSCRIPTION_TYPES = ['bag']; //非訂閱制類型會在專門的函數中處理（永久，沒有到期時間）
const NEED_CHECK_EXPIRY_TYPES = ['vip', 'mcd','bag']; // 非訂閱類型可以不參與檢查權限
const BUY_CONFIG = [
    'vip' => [
        'dir' => BASE_PATH,
        'log_file' => 'vip_expiry.log',
        'level' => [ //等級與費用， lv => cost
            1 => 688,
            2 => 1588,
            3 => 3399,
            4 => 7688,
            5 => 23999,
            6 => 128888
        ]
    ],
    'mcd' => [
        'dir' => BASE_PATH,
        'log_file' => 'mcd_expiry.log',
        'level' => [ //等級與費用
            1 => 799,
            2 => 2688
        ]
    ],
    'bag' => [
        'dir' => BASE_PATH,
        'log_file' => 'bag_level.log',
        'level' => [ //等級與費用
            1 => 360,
            2 => 1200,
            3 => 3600,
            4 => 6888,
            5 => 13999,
            6 => 36888
        ]
    ],
    // 可以添加更多的特權類型
];

const LUCKY_PACK_CONFIG = [
    'dir' => BASE_PATH,
    'log_file' => 'lucky_pack_claim.log',
    'rewards' => [
        [
            'type' => 'money',
            'amount' => 100,
            'chance' => 5
        ],
        [
            'type' => 'money',
            'amount' => 50,
            'chance' => 8
        ],
        [
            'type' => 'money',
            'amount' => 30,
            'chance' => 12
        ],
        [
            'type' => 'money',
            'amount' => 20,
            'chance' => 15
        ],
        [
            'type' => 'money',
            'amount' => 10,
            'chance' => 20
        ],
        [
            'type' => 'item',
            'item' => 'diamond',
            'amount' => 1,
            'chance' => 15
        ],
        [
            'type' => 'item',
            'item' => 'diamond',
            'amount' => 3,
            'chance' => 5
        ]
    ]
];

// 核心功能函數
function get_buy_config($type) {
    return BUY_CONFIG[$type] ?? null;
}

function init_user_dir($user, $dir) {
    if (!file_exists("$dir/$user")) {
        mkdir("$dir/$user", 0777, true);
    }
}
