# 自定义召唤物外观 / Customizing the summoned cube

召唤物不是硬编码贴图：渲染器调用原版方块模型系统绘制 `movinglightblock:moving_light_visual`。因此**任何标准 Minecraft 资源包**都能改它的材质和模型，无需改模组源码、无需重启游戏（`F3 + T` 重载资源包即可）。

仓库里附带一份可直接复制的模板：`example-resourcepack/`。

管理界面背景也可以用资源包替换。界面尺寸固定为 **176×166**，按放入的模块自动切换三张贴图：

```text
assets/movinglightblock/textures/gui/rolling_light_crafting.png   # 工作台 / 3×3 合成
assets/movinglightblock/textures/gui/rolling_light_smelting.png   # 熔炉 / 烧炼
assets/movinglightblock/textures/gui/rolling_light_storage.png    # 箱子 / 18 格储存
assets/movinglightblock/textures/gui/rolling_light_ui.png         # 空模块时的占位背景
```

槽位坐标与贴图上的格子一一对应：模型预览区在左上黑色方块内，由模组实时绘制，不需要画进贴图；玩家背包为 `x=8+18列, y=84+18行` 的 9×4 格。自定义贴图时请保留这些槽位位置。

## 方案 A：只替换贴图（最简单）

在任意资源包中放：

```text
assets/movinglightblock/textures/block/moving_light.png
```

该文件覆盖模组自带贴图。实体外观和创造模式物品图标会一起改变。

- 格式：标准 Minecraft 纹理，建议 16×16（也支持 32/64 等 2 的幂尺寸）
- 路径是固定的，不要改文件名

## 方案 B：完整自定义模型

在资源包中放置以下文件（`<ns>` 换成你自己的命名空间，例如 `mypack`）：

```text
assets/movinglightblock/blockstates/moving_light_visual.json
assets/movinglightblock/models/block/my_cube.json
assets/<ns>/textures/block/my_cube.png
```

`assets/movinglightblock/blockstates/moving_light_visual.json`：

```json
{
  "variants": {
    "": {
      "model": "<ns>:block/my_cube"
    }
  }
}
```

关键点：

- 方块状态文件**必须**叫 `moving_light_visual.json`，路径也必须是 `assets/movinglightblock/blockstates/`
- 变体 key 是空字符串 `""`，因为这个方块没有额外 BlockState 属性
- `model` 指向你自己的模型

`assets/movinglightblock/models/block/my_cube.json`（标准原版 block model 格式）：

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "<ns>:block/my_cube"
  }
}
```

也可以不用 `parent`，直接写 `elements` 自定义形状和 UV：

```json
{
  "textures": {
    "all": "<ns>:block/my_cube"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down":  { "texture": "#all", "uv": [0, 0, 16, 16] },
        "up":    { "texture": "#all", "uv": [0, 0, 16, 16] },
        "north": { "texture": "#all", "uv": [0, 0, 16, 16] },
        "south": { "texture": "#all", "uv": [0, 0, 16, 16] },
        "west":  { "texture": "#all", "uv": [0, 0, 16, 16] },
        "east":  { "texture": "#all", "uv": [0, 0, 16, 16] }
      }
    }
  ]
}
```

## 滚动行为（rolling.json）

在资源包中放这个文件即可自定义滚动方式：

```text
assets/movinglightblock/rolling.json
```

默认内容（模板里已经带了一份）：

```json
{
  "enabled": true,
  "axis": "auto",
  "degreesPerBlock": 90.0,
  "speed": 1.0
}
```

| 字段 | 默认 | 说明 |
|---|---:|---|
| `enabled` | `true` | 是否滚动；`false` 时实体保持直立。也可用 `axis: "none"` |
| `axis` | `"auto"` | `auto` / `x` / `y` / `z` / `none` / `free` / `euler`，或直接写 `[x, y, z]` 数组 |
| `freeAxis` | `[0, 1, 0]` | `axis: "free"` 时的自定义单位轴；也接受 `{"x":0,"y":1,"z":0}` |
| `degreesPerBlock` | `90.0` | 每移动 1 格滚动的角度；数组形式 `[x,y,z]` 用于欧拉模式 |
| `euler` | — | 欧拉模式下的每格三轴角度，可写数组或对象，优先于数组形式的 `degreesPerBlock` |
| `speed` | `1.0` | 速度倍率；`2.0` 是两倍滚动量，`0.0` 则不滚 |

固定轴模式：

- `auto`：绕“上方向 × 水平位移”翻滚，原来的伙伴方块式滚动
- `x`：绕世界 X 轴，按 Z 方向位移滚动（前后翻）
- `y`：绕世界 Y 轴，按水平位移自转（像陀螺）
- `z`：绕世界 Z 轴，按 X 方向位移滚动（左右翻）
- `none`：不滚

## 自由轴（任意方向）

最直接的方式是把 `axis` 写成三维方向向量：

```json
{
  "enabled": true,
  "axis": [1.0, 0.35, 0.2],
  "degreesPerBlock": 90.0,
  "speed": 1.0
}
```

等价写法：

```json
{
  "enabled": true,
  "axis": "free",
  "freeAxis": [1.0, 0.35, 0.2],
  "degreesPerBlock": 90.0,
  "speed": 1.0
}
```

向量会自动归一化； `[0,0,0]` 无效，会退回 `auto`。自由轴模式按**实际移动距离**累计角度，不按分量投影。

## 三轴旋转 / Euler 模式

如果希望同时指定绕 X、Y、Z 的旋转量：

```json
{
  "enabled": true,
  "axis": "euler",
  "degreesPerBlock": [90.0, 45.0, 0.0],
  "speed": 1.0
}
```

也可以写对象形式：

```json
{
  "enabled": true,
  "axis": "euler",
  "euler": { "x": 90.0, "y": 45.0, "z": 0.0 },
  "speed": 1.0
}
```

旋转顺序固定为 **XYZ**（先 X，再 Y，再 Z）。顺序不能省略：三维旋转不满足交换律，先绕 X 再绕 Y 和反过来结果不同。

模板目录里附带了两个可直接改的示例：

```text
example-resourcepack/examples/rolling-free-axis.json
example-resourcepack/examples/rolling-euler.json
```

## 常见三轴旋转表示法（背景）

| 表示法 | 编辑方式 | 优点 | 缺点 |
|---|---|---|---|
| 欧拉角 | 三个角度 X/Y/Z + 顺序（XYZ、ZYX…） | 直观，Blockbench/Blender 常用 | 顺序敏感，会有万向节死锁 |
| 轴角 / 旋转向量 | 一个方向轴 + 一个角度 | 最适合“自由轴”，物理含义清楚 | 可视化不如三个角度直观 |
| 四元数 | `(x, y, z, w)` 四个数 | 无死锁，游戏引擎内部标准 | 手调不直观 |
| 旋转矩阵 | 3×3 矩阵 | 数学定义完整 | 9 个数，最不适合手填 |

这个模组的渲染器内部已经用四元数累积姿态；`rolling.json` 只是**编写格式**：固定轴/自由轴会被转成轴角，再转成四元数；欧拉模式则按 XYZ 顺序转成四元数。

说明：

- 文件缺失或字段缺失时自动使用默认值，不做资源包也能正常工作
- `config/movinglightblock-common.toml` 里的 `animation` 仍然是总开关（同步到客户端），设 `false` 会强制不滚，资源包无法覆盖
- 改完 `rolling.json` 用 `F3 + T` 重载资源包即可，滚动状态会重置

## 动画贴图

在贴图旁放同名 `.mcmeta` 文件：

```text
assets/<ns>/textures/block/my_cube.png.mcmeta
```

内容使用原版动画格式：

```json
{
  "animation": {
    "frametime": 2
  }
}
```

## 当前限制

渲染器固定使用 `RenderType.solid()` 和最高亮度 `FULL_BRIGHT`：

- ✅ 支持：任意模型形状、贴图、UV、动画贴图
- ❌ 不支持：透明/半透明渲染层、按环境光变暗（实体永远全亮）

如需透明材质或按模型自动选择渲染层，需要改 `RollingLightRenderer`（可改为 `ItemBlockRenderTypes.getRenderType(...)`）。

## 自定义声音（独立文件夹 + JSON）

声音按钮是唯一会创建 `config/movinglightblock/sounds/` 的操作；不打开声音设置界面就不会生成这个文件夹。把 `.ogg` 文件放进去，文件夹内的 `sounds.json` 是唯一的声音信息来源：

```json
{
  "summon": { "file": "spawn.ogg", "volume": 1.0, "speed": 1.0 },
  "land":   { "file": "thud.ogg",  "volume": 0.8, "speed": 1.0 },
  "move":   { "file": "roll.ogg",  "volume": 0.5, "speed": 1.2 },
  "idle":   { "file": "hum.ogg",   "volume": 0.3, "speed": 1.0 }
}
```

| 阶段 | 触发方式 |
|---|---|
| `summon` | 自己的方块第一次出现在客户端，播放一次 |
| `land` | 下落结束并停住，播放一次 |
| `move` | 开始移动时循环播放，停下即停止 |
| `idle` | 从移动变为静止时播放一次 |

`file` 留空表示无声（默认值）；`volume` 范围 0–2，`speed` 范围 0.25–4。`speed` 同时改变音高，和原版音效的 pitch 行为一致。所有字段都可以在游戏内的声音设置界面里修改，保存后立即作用于正在播放的循环音效。声音只在本地播放，不占用原版 `sounds.json`，也不需要资源包。

## 使用内置模板

1. 把仓库中的 `example-resourcepack/` 整个文件夹复制到：
   - 整体实例：`AppData/Roaming/.minecraft/resourcepacks/`
   - 版本隔离实例：`AppData/Roaming/.minecraft/versions/1.21.1-NeoForge/resourcepacks/`
2. 在游戏资源包页面启用它（`pack_format` 为 `34`，对应 Minecraft 1.21.1）
3. 替换 `assets/movinglightblock/textures/block/moving_light_custom.png` 为你自己的贴图
4. 若要改模型形状，编辑 `assets/movinglightblock/models/block/moving_light_custom.json`
5. `F3 + T` 重载资源包

模板默认启用的是方案 B（完整模型），因此改贴图或改模型都会立即反映到实体上。
