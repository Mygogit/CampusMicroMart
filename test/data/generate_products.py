"""
校园二手商品数据生成脚本
生成模拟校园二手交易平台的商品数据，支持 SQL 和 JSON 格式输出。
"""

import argparse
import json
import random
import sys
from datetime import datetime, timedelta

# 校园场景数据
COURSE_CODES = [
    "CS101", "CS201", "CS301", "MATH101", "MATH201", "ENG101", "ENG201",
    "PHY101", "CHM101", "BIO101", "ECON101", "ECON201", "MGMT101", "MGMT201",
    "LAW101", "PSY101", "SOC101", "ART101", "MUS101", "PE101", "HIST101",
    "JAVA101", "PYTHON201", "AI301", "DB101", "NET201", "DS301", "ML401"
]

DORMITORIES = [
    "东区1号楼", "东区2号楼", "东区3号楼", "东区4号楼",
    "西区1号楼", "西区2号楼", "西区3号楼", "西区4号楼",
    "南区1号楼", "南区2号楼", "南区3号楼", "北区1号楼",
    "北区2号楼", "研究生1号楼", "研究生2号楼", "留学生公寓"
]

CATEGORIES = [
    ("教材教辅", 1), ("电子数码", 2), ("生活用品", 3),
    ("运动户外", 4), ("服装鞋帽", 5), ("图书音像", 6),
    ("文具用品", 7), ("美妆护肤", 8), ("零食饮品", 9), ("其他", 10)
]

PRODUCT_NAME_TEMPLATES = [
    # 教材教辅
    ("《{course}》教材", 1), ("{course} 习题集", 1), ("{course} 实验指导书", 1),
    ("{course} 考研笔记", 1), ("{course} 期末考试真题", 1),
    # 电子数码
    ("二手键盘", 2), ("机械键盘 Cherry轴", 2), ("蓝牙耳机", 2),
    ("显示器 24寸", 2), ("笔记本散热支架", 2), ("手机充电器套装", 2),
    ("移动硬盘 1TB", 2), ("USB扩展坞", 2), ("电脑屏幕挂灯", 2),
    # 生活用品
    ("台灯 LED护眼", 3), ("床上书桌", 3), ("收纳箱 大号", 3),
    ("保温杯 500ml", 3), ("宿舍小风扇", 3), ("衣架 10个装", 3),
    ("瑜伽垫加厚", 3), ("小功率电煮锅", 3),
    # 运动户外
    ("二手篮球", 4), ("羽毛球拍一副", 4), ("瑜伽垫", 4),
    ("跳绳", 4), ("跑步运动鞋 42码", 4), ("哑铃 5kg×2", 4),
    # 服装鞋帽
    ("冬季棉服 中长款", 5), ("帆布鞋 经典款", 5), ("双肩包 商务款", 5),
    ("围巾 羊毛混纺", 5), ("帽子 棒球帽", 5),
    # 图书音像
    ("《人月神话》", 6), ("《设计模式》", 6), ("《算法导论》", 6),
    ("《代码整洁之道》", 6), ("英语四级词汇", 6), ("日语入门教材", 6),
    # 其他
    ("{course} 网课账号", 10), ("{course} 实验报告", 10),
]

DESCRIPTIONS = [
    "九成新，仅用过一学期",
    "全新未拆封",
    "八成新，有轻微使用痕迹",
    "急出，价格可小刀",
    "毕业清仓，低价处理",
    "买多了用不完，便宜转",
    "质量很好，考研必备",
    "几乎全新，买来没用过几次",
    "正常使用痕迹，功能完好",
    "学长推荐，考试必备资料",
]


def generate_product_name(category_id):
    """生成商品名称"""
    templates = [t for t in PRODUCT_NAME_TEMPLATES if t[1] == category_id]
    if not templates:
        templates = PRODUCT_NAME_TEMPLATES
    template = random.choice(templates)[0]
    if "{course}" in template:
        template = template.format(course=random.choice(COURSE_CODES))
    return template


def generate_price(category_id):
    """根据分类生成合理价格"""
    price_ranges = {
        1: (5, 80),    # 教材：5-80
        2: (20, 800),  # 电子：20-800
        3: (5, 100),   # 生活：5-100
        4: (10, 200),  # 运动：10-200
        5: (15, 150),  # 服装：15-150
        6: (3, 50),    # 图书：3-50
        7: (2, 30),    # 文具：2-30
        8: (10, 120),  # 美妆：10-120
        9: (3, 40),    # 零食：3-40
        10: (10, 200), # 其他：10-200
    }
    low, high = price_ranges.get(category_id, (10, 100))
    return round(random.uniform(low, high), 2)


def generate_products(n):
    """生成 n 条商品数据"""
    products = []
    base_time = datetime.now() - timedelta(days=90)

    for i in range(1, n + 1):
        category_name, category_id = random.choice(CATEGORIES)
        product_status = random.choices([0, 1, 2, 3, 4], weights=[1, 6, 1, 1, 1])[0]
        audit_status = 1 if product_status != 0 else random.choices([0, 2], weights=[7, 3])[0]
        stock = random.randint(1, 10)
        price = generate_price(category_id)
        create_time = base_time + timedelta(
            days=random.randint(0, 90),
            hours=random.randint(0, 23),
            minutes=random.randint(0, 59)
        )

        product = {
            "name": generate_product_name(category_id),
            "description": random.choice(DESCRIPTIONS),
            "price": price,
            "stock": stock,
            "stock_threshold": 5,
            "product_status": product_status,
            "audit_status": audit_status,
            "category_id": category_id,
            "user_id": random.randint(1, 50),
            "images": "",
            "course_code": random.choice(COURSE_CODES),
            "dormitory": random.choice(DORMITORIES),
            "create_time": create_time.strftime("%Y-%m-%d %H:%M:%S"),
            "update_time": create_time.strftime("%Y-%m-%d %H:%M:%S"),
        }
        products.append(product)

    return products


def to_sql(products):
    """输出 SQL INSERT 语句"""
    print("-- 校园二手商品测试数据")
    print("-- 生成时间: " + datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    print("-- 数据条数: " + str(len(products)))
    print()
    print("USE campus_product;")
    print()

    for p in products:
        sql = (
            f"INSERT INTO t_product "
            f"(name, description, price, stock, stock_threshold, product_status, "
            f"audit_status, category_id, user_id, course_code, dormitory, create_time, update_time) "
            f"VALUES ("
            f"'{p['name'].replace(chr(39), chr(39)+chr(39))}', "
            f"'{p['description']}', "
            f"{p['price']}, {p['stock']}, {p['stock_threshold']}, {p['product_status']}, "
            f"{p['audit_status']}, {p['category_id']}, {p['user_id']}, "
            f"'{p['course_code']}', '{p['dormitory']}', "
            f"'{p['create_time']}', '{p['update_time']}'"
            f");"
        )
        print(sql)


def to_json(products):
    """输出 JSON 格式"""
    output = {
        "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "count": len(products),
        "products": products,
    }
    json.dump(output, sys.stdout, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(
        description="校园二手商品数据生成器",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python generate_products.py -n 10 -f sql
  python generate_products.py -n 100 -f json > products.json
  python generate_products.py -n 1000 -f sql > data.sql
        """
    )
    parser.add_argument("-n", "--count", type=int, default=100,
                        help="生成数据条数 (默认: 100)")
    parser.add_argument("-f", "--format", choices=["sql", "json"], default="sql",
                        help="输出格式 (默认: sql)")
    parser.add_argument("-s", "--seed", type=int, default=None,
                        help="随机种子 (用于可重复生成)")
    args = parser.parse_args()

    if args.seed is not None:
        random.seed(args.seed)

    products = generate_products(args.count)

    if args.format == "sql":
        to_sql(products)
    else:
        to_json(products)


if __name__ == "__main__":
    main()
