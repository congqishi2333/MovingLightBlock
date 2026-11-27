# 自定义召唤物外观 / Customizing the summoned cube

召唤物不是硬编码贴图：渲染器调用原版方块模型系统绘制 `movinglightblock:moving_light_visual`。因此**任何标准 Minecraft 资源包**都能改它的材质和模型，无需改模组源码、无需重启游戏（`F3 + T` 重载资源包即可）。

仓库里附带一份可直接复制的模板：`example-resourcepack/`。

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

## 使用内置模板

1. 把仓库中的 `example-resourcepack/` 整个文件夹复制到：
   - 整体实例：`AppData/Roaming/.minecraft/resourcepacks/`
   - 版本隔离实例：`AppData/Roaming/.minecraft/versions/1.21.1-NeoForge/resourcepacks/`
2. 在游戏资源包页面启用它（`pack_format` 为 `34`，对应 Minecraft 1.21.1）
3. 替换 `assets/movinglightblock/textures/block/moving_light_custom.png` 为你自己的贴图
4. 若要改模型形状，编辑 `assets/movinglightblock/models/block/moving_light_custom.json`
5. `F3 + T` 重载资源包

模板默认启用的是方案 B（完整模型），因此改贴图或改模型都会立即反映到实体上。
