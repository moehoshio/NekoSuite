<?php
// cdk 核心配置
const USER_BASE_PATH = "/web/neko.moe/usr";
const PUBLIC_CDK_PATH = "/web/neko.moe/public/cdk"; //一次性cdk與公共cdk存放目錄

const CDK_CONFIG = [
    'onec_time' => [ //只能使用一次的cdk
        'cdk1' => [
            'reward' => 'reward1',
            'expiry' => '2025-01-01'
        ]
    ],
    'user_once' => [ //每位用戶可使用一次的cdk
        'NekoServer2025' => [
            'reward' => 'NekoServer2025',
            'expiry' => '2026-01-01'
        ]
    ],
    'total_limit' => [ //公共可使用N次的cdk，每位用戶一次。
        'cdk3' => [
            'reward' => 'reward3',
            'expiry' => '2025-01-01',
            'limit' => 100
        ]
    ]
];

function get_cdk_config($type, $cdk) {
    return CDK_CONFIG[$type][$cdk] ?? null;
}

function init_cdk_dir($dir) {
    if (!file_exists($dir)) {
        mkdir($dir, 0777, true);
    }
}