<?php
// 核心配置
const BASE_PATH = "/web/neko.moe/usr";
const WISH_CONFIG = [

    // 機率說明：目前的得獎模式爲 基礎機率 + 保底機率，即當祈願次數達到保底次數時，必定獲得保底物品。
    // 機率模型說明： 設計期望玩家獲得大獎的次數爲 200 次，即目標機率爲0.5%。故基礎機率爲0.246%，保底機率約爲0.3334% ，合計 ≈ 0.579。

    'normal' => [ //普通祈願
        'dir' => BASE_PATH,
        'log_file' => 'normal.log', // 祈願記錄文件，記錄一個int值，代表已經祈願的次數
        'max_count' => 299, // 祈願保底期望次數，下次抽獎必定獲得
        'items'=> [ // 祈愿物品id及概率
            "iron_ingot5" => 20,
            "exp20" =>20,
            "gold_ingot3" => 16,
            "diamond" => 10,
            "exp100" =>10,
            "p_point5" => 10,
            "netherite_scrap" => 5,
            "potion_power" =>5, //力量Ⅲ*180s
            "rain_clouds" => 1, //雨云粒子效果
            "KirinOverlordBlazingArmor" =>0.246 //麒麟霸炎铠
        ],
        'guarantee_items' => [ // 達到保底時必定獲得的物品
            "KirinOverlordBlazingArmor_guarantee" //麒麟霸炎铠，但額外顯示資訊爲保底得到的物品
        ]
    ],
    'valentine' => [ //情人節祈願
        'dir' => BASE_PATH,
        'log_file' => 'valentine.log',
        'max_count' => 299,
        'items'=> [
            "prismarine_crystals5" => 20,
            "quartz5" => 20,
            "exp20" => 20,
            "flower_pie3" =>10,
            "quartz10" =>10,
            "enchanted_golden_apple" =>5,
            "pink_cherry_sapling" =>5, // 粉樱花树苗
            "amethyst_chimes" => 3, // 紫水晶风铃
            "potion_revive" => 3, // 生命恢复Ⅱ
            "UnrivaledCrimsonFeatherBow" =>0.246, // 无双赤羽弓
        ],
        'guarantee_items' => [
            "UnrivaledCrimsonFeatherBow_guarantee" //无双赤羽弓，但額外顯示資訊爲保底得到的物品
        ]
    ],
    'daily' => [ //每日祈願
        'dir' => BASE_PATH,
        'log_file' => 'daily.log', //記錄一個日期 ，範例 2025-01-01，每天只能祈願一次，日期相同代表今天已經祈願過了
        'date_file' => 'daily_date.log',
        'max_count' => 99, // 每日祈願多少次，下次必定獲得神裝
        'items' => [
            "bal5" =>25, //金幣*5
            "bal10" =>20,
            "bal30" =>8,
            "bal50" =>3,
            "bal100" =>1,
            "bal360" =>0.3,
            "bal600" =>0.2,
            "bal1200" =>0.1,
            "bal3600" =>0.1,
            "bal1w" =>0.01, //金幣*10,000
            "exp20" => 20,
            "exp100" => 10,
            "ppoint5" => 10, //P點*5
            "op_equip" => 0.01, //隨機神裝
            "potion"=> 20 //隨機藥水
        ],
        'guarantee_items' => [ //隨機神裝
            "CloudspringAzureBlade", //碧波雲泉劍
            "UnrivaledCrimsonFeatherBow", //無雙赤羽弓
            "KirinOverlordBlazingArmor" //麒麟霸炎鎧
        ],
        
        'potion' => [ //隨機藥水
            "minecraft:speed" => 10,
            "minecraft:slowness" => 10,
            "minecraft:haste" => 10,
            "minecraft:mining_fatigue" => 10,
            "minecraft:strength" => 10,
            "minecraft:instant_health" => 10,
            "minecraft:instant_damage" => 10,
            "minecraft:jump_boost" => 10,
            "minecraft:nausea" => 10,
            "minecraft:regeneration" => 10,
            "minecraft:resistance" => 10,
            "minecraft:fire_resistance" => 10,
            "minecraft:water_breathing" => 10,
            "minecraft:invisibility" => 10,
            "minecraft:blindness" => 10,
            "minecraft:night_vision" => 10,
            "minecraft:hunger" => 10,
            "minecraft:weakness" => 10,
            "minecraft:poison" => 10,
            "minecraft:wither" => 10,
            "minecraft:health_boost" => 10,
            "minecraft:absorption" => 10,
            "minecraft:saturation" => 10,
            "minecraft:glowing" => 10,
            "minecraft:levitation" => 10,
            "minecraft:luck" => 10,
            "minecraft:unluck" => 10
        ]
    ]
];

// 核心功能函數
function get_wish_config($type) {
    return WISH_CONFIG[$type] ?? null;
}

function init_user_dir($user, $dir) {
    if (!file_exists("$dir/$user")) {
        mkdir("$dir/$user", 0777, true);
    }
}

function handle_guarantee($user, $type) {
    $config = get_wish_config($type);
    if (!$config) return false;
    
    $log_path = "{$config['dir']}/$user/{$config['log_file']}";
    $current = file_exists($log_path) ? (int)file_get_contents($log_path) : 0;
    
    if ($current >= $config['max_count']) {
        file_put_contents($log_path, $current - $config['max_count']);
        $guarantee_item_key = array_rand($config['guarantee_items']);
        return $config['guarantee_items'][$guarantee_item_key];
    }
    return false;
}

function update_wish_count($user, $type, $value) {
    $config = get_wish_config($type);
    if (!$config) return 0;
    
    $log_path = "{$config['dir']}/$user/{$config['log_file']}";
    $current = file_exists($log_path) ? (int)file_get_contents($log_path) : 0;
    $new_value = $current + $value;
    
    file_put_contents($log_path, $new_value);
    return $new_value;
}

function get_random_item($items) {
    $total = array_sum($items); // 總概率
    $rand = mt_rand(1, $total * 1000) / 1000; // 生成隨機數，這樣能夠處理三位小數點
    foreach ($items as $item => $chance) {
        if ($rand <= $chance) {
            return $item;
        }
        $rand -= $chance;
    }
    return null;
}