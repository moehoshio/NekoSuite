import requests
import matplotlib.pyplot as plt
from collections import Counter
from matplotlib import rcParams

# API 設定
BASE_URL = "http://neko.moe/wish/wish.php?"
USER_NAME = "test_user"
WISH_TYPE = "normal"  # 可選 normal / valentine / daily 。 如果要測試每日祈願，需要將handle_wish_action函式中處理每日祈願邏輯的代碼註釋掉。
WISH_COUNT = 10  # 每次測試祈願次數
TOTAL_TRIALS = 10000  # 測試總次數

rcParams['font.sans-serif'] = ['SimHei']  # 使用黑體
rcParams['axes.unicode_minus'] = False  # 防止負號顯示為亂碼

# 物品 ID 對應物品名稱
ITEM_MAPPING = {
    "normal" : {
        "iron_ingot5": "鐵錠*5",
        "gold_ingot3": "金锭*3",
        "diamond": "鑽石*1",
        "netherite_scrap": "下界合金碎片",
        "exp20": "經驗*20",
        "exp100": "經驗*100",
        "rain_clouds": "雨雲粒子效果",
        "potion_power": "力量Ⅲ*180s",
        "p_point5": "P点*5",
        "KirinOverlordBlazingArmor": "麒麟霸炎铠",
        "KirinOverlordBlazingArmor_guarantee": "保底:麒麟霸炎铠"
    },
    "valentine" : {
        "prismarine_crystals5": "海晶沙砾*5",
        "quartz5": "石英*5",
        "flower_pie3": "鲜花饼*3",
        "enchanted_golden_apple": "附魔金苹果",
        "exp20": "经验*20",
        "quartz10": "石英*10",
        "pink_cherry_sapling": "粉樱花树苗",
        "amethyst_chimes": "紫水晶风铃",
        "potion_revive": "生命恢复Ⅱ",
        "UnrivaledCrimsonFeatherBow": "无双赤羽弓",
        "UnrivaledCrimsonFeatherBow_guarantee": "保底:无双赤羽弓"
    },
    "daily" : {
        "bal5": "金幣*5",
        "bal10": "金幣*10",
        "bal30": "金幣*30",
        "bal50": "金幣*50",
        "bal100": "金幣*100",
        "bal360": "金幣*360",
        "bal600": "金幣*600",
        "bal1200": "金幣*1200",
        "bal3600": "金幣*3600",
        "bal1w": "金幣*1w",
        "exp20": "經驗*20",
        "exp100": "經驗*100",
        "ppoint5": "P點*5",
        "CloudspringAzureBlade": "碧波雲泉劍",
        "UnrivaledCrimsonFeatherBow": "無雙赤羽弓",
        "KirinOverlordBlazingArmor": "麒麟霸炎鎧",
        "potion": "隨機藥水"
    }
}

# 統計結果
wish_results = []


def make_wish():
    """發送祈願請求"""
    params = {
        "action": "wish",
        "user": USER_NAME,
        "type": WISH_TYPE,
        "wish": WISH_COUNT
    }
    response = requests.get(BASE_URL, params=params)
    if response.status_code == 200:
        return response.text  # 返回純文本
    return None

print("now ready request")

# 進行多次祈願測試
for _ in range(TOTAL_TRIALS // WISH_COUNT):
    result = make_wish()
    if result:
        parsed_items = result.strip().split()[2:]  # 解析物品ID
        wish_results.extend(parsed_items)

# 轉換為物品名稱並計算出現次數
item_names = [ITEM_MAPPING[WISH_TYPE].get(item, f"未知物品({item})") for item in wish_results]
item_counts = Counter(item_names)

print("request is okay , now plot")

# 繪製圖表代碼
def plot_results():
    """繪製祈願結果柱狀圖"""
    items, counts = zip(*item_counts.items())
    plt.figure(figsize=(12, 6))
    bars = plt.bar(items, counts, color='skyblue')
    
    # 在柱狀圖上方標註次數
    for bar, count in zip(bars, counts):
        plt.text(bar.get_x() + bar.get_width() / 2, bar.get_height(), str(count), 
                 ha='center', va='bottom', fontsize=10, color='black')
    

    plt.xlabel("Item")
    plt.ylabel("Frequency")
    # 更新標題，顯示本次祈願的總次數
    plt.title(f"Wish Probability Distribution ({WISH_TYPE}) - Total Wishes: {TOTAL_TRIALS}")
    plt.xticks(rotation=45, ha="right")
    plt.subplots_adjust(bottom=0.20)
    plt.show()

plot_results()
