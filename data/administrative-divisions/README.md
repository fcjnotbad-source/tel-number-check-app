# 离线行政名称与隶属关系

此资源仅用于识别搜索结果已经出现的真实省市名称及其隶属关系，防止把“广告推销”“美团”等正文猜成城市。它不包含电话号码、号段、电话区号、号码归属地或骚扰标记，也不根据号码推断地点。

## 来源与版本

- 开源项目：[uiwjs/province-city-china](https://github.com/uiwjs/province-city-china)。项目 README 说明其行政名称数据来自民政部及国家统计局；本项目仅导入该项目直接发布的 JSON，不调用这些政府接口。
- 固定提交：[a708f0df0ebdb68f09f5fcf6345367f7d5e9a7dc](https://github.com/uiwjs/province-city-china/tree/a708f0df0ebdb68f09f5fcf6345367f7d5e9a7dc)，提交时间 2026-10-11。
- 导入日期：2026-10-22。这是固定的开源发布快照，不是实时行政区划查询。源 `area.json` 最近一次修改是 [9dd754323886a322995ae55e1962202bc7939170](https://github.com/uiwjs/province-city-china/commit/9dd754323886a322995ae55e1962202bc7939170)，时间 2026-10-09；不把源 README 中旧的示例时间或仓库依赖更新时间当作行政数据官方截止日期。
- 原始文件位于固定提交的 [`packages/core/dist/`](https://github.com/uiwjs/province-city-china/tree/a708f0df0ebdb68f09f5fcf6345367f7d5e9a7dc/packages/core/dist)：`province.json`、`city.json`、`area.json`。
- 分发许可：[MIT 原文](https://github.com/uiwjs/province-city-china/blob/a708f0df0ebdb68f09f5fcf6345367f7d5e9a7dc/LICENSE)，版权行为 `Copyright (c) 2019 wabg`。完整许可保存在本目录 `LICENSE`；同一许可也随 APK 资源 `geo/LICENSE-uiw-province-city-china.txt` 分发。

## 资源格式与覆盖范围

`app/src/main/resources/geo/province-cities.tsv` 是 UTF-8、LF、无表头 TSV，每行四列：

```text
provinceCode<TAB>provinceFullName<TAB>cityCode<TAB>cityFullName
```

代码均保留上游六位字符串，不自行生成行政编码。当前有 **370 对**，覆盖大陆 31 个省级行政区：333 个真实地级行政名称、4 个直辖市同名配对、33 个上游明确列出的省或自治区直属县级真实名称。

- 北京、天津、上海、重庆没有另造“市辖区”城市；直接使用同一上游省级代码和名称做同名配对，例如 `110000 北京市 110000 北京市`。
- 保留真实的地区、自治州、盟等地级名称，未将它们强行改称“市”。
- `省直辖县级行政区划`、`自治区直辖县级行政区划`、`市辖区`、`县` 等统计汇总项不作为城市。对于上游直管汇总项，只导入 `area.json` 中对应的真实子项，如济源市、仙桃市、神农架林区、石河子市等。
- 上游 `province.json` 虽列出台湾、香港、澳门省级名称，但本次选取的 `city.json` 没有对应城市层级。本 TSV **不包含港澳台省市配对**，也不把区名或虚构同名城市补入。未知或未覆盖名称应保留搜索原文。
- 此表不是完整地址库：普通区县、街道、门牌地址不导入；只有省/自治区直属县级真实名称作为必要例外。完整企业名称和详细地址仍应由展示层保守保留。

## 可复现导入

仅需 Python 3 标准库，运行时无新增 SDK 或网络依赖：

```bash
python3 scripts/import-administrative-divisions.py
python3 scripts/import-administrative-divisions.py --check
```

脚本只从固定 GitHub 提交下载上述三个 JSON 与 LICENSE 到系统临时目录，校验 SHA-256，然后机械生成 TSV；不会访问电话、搜索引擎或账号数据。已下载的固定源文件也可离线复现：

```bash
python3 scripts/import-administrative-divisions.py --source-dir /path/to/pinned-json-files --check
```

源文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| province.json | `7d7ee447e058bec7e2918ee78241be1341c4471ac8483e6b34eecfd2e154796e` |
| city.json | `450f63fd503878c5a5fee36aa539dce0916a91e48200aa49034320eb4ff9185e` |
| area.json | `c286dffaf5d8b28316f77a343d3d0bba01efbc0570780bc90ef81a71fc73dca1` |
| LICENSE | `becf557b4dbb188022e699c2a2eb84828325252e992796b9e102deb303348359` |

升级数据时必须重新审核固定提交、覆盖范围与许可，并显式更新脚本中的哈希；不要将导入改为运行时自动联网更新。
