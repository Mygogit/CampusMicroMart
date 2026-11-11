# CampusMicroMart Git 工作流(简化版)

> 毕业设计单体仓库,当前为单人开发。与 SQBAPA 团队仓库**共用同一套提交规范与钩子**,
> 但采用更轻量的分支策略(主干开发 + 功能分支 + 版本 tag),不强制完整 Git Flow。

## 规范(与团队一致)

- 提交信息必须遵循 Conventional Commits:`<type>(<scope>): <subject>`
  - type:`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
  - 例:`feat(payment): 支持模拟支付重试` / `fix(order): 修复库存超卖`
- 原子提交:一次提交只做一件事。
- 禁止把密钥 / `.env` 提交进库(钩子会拦截)。
- 已通过本地 `.mailmap` 把两个 Git 身份归一（该映射文件不入库）。

## 分支策略

```
main  ───●────●────●────●───  主干, 答辩/交付时打 tag v1.0 / v2.0
          \  /    \  /
feature/*  ●──●    ●──●      功能/缺陷分支, 完成后合回 main
```

- 日常在 `main` 上开发即可;较大功能开 `feature/<名>` 分支,完成后合并。
- 重要里程碑打 tag:`git tag -a v1.0 -m "毕业设计 v1.0"`。
- 如需多人协作,直接套用 SQBAPA 的 Git Flow(见其 GIT_WORKFLOW.md)。

## 钩子

`.githooks/` 下的 `commit-msg`(校验提交格式)与 `pre-commit`(密钥扫描)已启用,
由 `core.hooksPath` 配置。克隆后无需额外操作即生效。

## 远程

- `origin` → GitHub: https://github.com/Mygogit/CampusMicroMart.git
- 首次克隆:`git clone https://github.com/Mygogit/CampusMicroMart.git`
