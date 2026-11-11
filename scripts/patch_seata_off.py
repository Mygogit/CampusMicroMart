# -*- coding: utf-8 -*-
"""
将 4 个微服务 jar 内嵌的 BOOT-INF/classes/application-standalone.yml
中的 seata.enabled: true 翻转为 false（仅翻转 seata: 直下的 enabled）。
原因: 本机 standalone 未起 Seata TC(8091), payment 的 @GlobalTransactional
强依赖 TC, 缺失即抛 500。关闭 standalone 的 Seata 后走本地事务即可。

采用 zipfile 原地改写, 规避 jar.exe 在含中文路径/工作目录下的抽取异常。
回滚点: 各 jar 同目录的 *.jar.seata.bak (改动前原始副本)。
"""
import io
import os
import re
import sys
import time
import zipfile

BASE = r"E:\数据\CampusMicroMart\dist-standalone\app"
SERVICES = ["payment", "order", "product", "user"]
ENTRY = "BOOT-INF/classes/application-standalone.yml"
ENABLED_RE = re.compile(r"enabled:\s*true")


def patch_one(jar_path):
    if not os.path.exists(jar_path):
        return f"[SKIP] 无 jar: {jar_path}"
    tmp_path = jar_path + ".patched"
    found = False
    changed = False
    try:
        with zipfile.ZipFile(jar_path, "r") as zin:
            infos = zin.infolist()
            with zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as zout:
                for info in infos:
                    data = zin.read(info.filename)
                    if info.filename == ENTRY:
                        found = True
                        text = data.decode("utf-8")
                        lines = text.split("\n")
                        for i in range(1, len(lines)):
                            if lines[i - 1].strip() == "seata:" and ENABLED_RE.search(lines[i]):
                                lines[i] = ENABLED_RE.sub("enabled: false", lines[i])
                                changed = True
                        if changed:
                            data = ("\n".join(lines)).encode("utf-8")
                    # 保留原 ZipInfo(名称/顺序/压缩方式/时间戳), 仅替换目标条目内容
                    zout.writestr(info, data)
    except Exception as e:  # noqa: BLE001
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        return f"[FAIL] {os.path.basename(jar_path)} 处理异常: {e}"

    if not found:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        return f"[SKIP] {os.path.basename(jar_path)} 无 {ENTRY}"
    if not changed:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        return f"[SKIP] {os.path.basename(jar_path)} 已是 false 或无 enabled:true"

    # 用改写后的 jar 替换原文件 (停服后可能有短暂锁, 重试几次)
    replaced = False
    for attempt in range(1, 11):
        try:
            os.replace(tmp_path, jar_path)
            replaced = True
            break
        except PermissionError:
            if attempt < 10:
                time.sleep(2)
            else:
                raise
    if not replaced:
        return f"[FAIL] {os.path.basename(jar_path)} 替换失败(文件被占用)"
    return f"[OK] {os.path.basename(jar_path)} -> seata.enabled=false"


def verify_one(jar_path):
    try:
        with zipfile.ZipFile(jar_path, "r") as z:
            data = z.read(ENTRY)
        text = data.decode("utf-8")
        m = re.search(r"seata:[\s\S]*?enabled:\s*(true|false)", text)
        return f"      校验 {os.path.basename(jar_path)}: seata.enabled={m.group(1) if m else 'N/A'}"
    except Exception as e:  # noqa: BLE001
        return f"      校验失败 {os.path.basename(jar_path)}: {e}"


def main():
    print("==== 注入 seata.enabled=false (Python zipfile) ====")
    for svc in SERVICES:
        jar = os.path.join(BASE, f"{svc}-service.jar")
        msg = patch_one(jar)
        print(msg)
        if msg.startswith("[OK]"):
            print(verify_one(jar))
    print("==== 完成 ====")


if __name__ == "__main__":
    main()
