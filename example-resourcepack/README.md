# 召唤物外观模板 / Cube appearance template

这是一个**可直接放进资源包文件夹**的模板，用于自定义 `MovingLightBlock` 召唤物的外观。

## 使用方法

1. 复制整个 `example-resourcepack` 文件夹到 Minecraft 的 `resourcepacks` 目录：
   - 整体实例：`%appdata%/.minecraft/resourcepacks/`
   - 版本隔离实例：`%appdata%/.minecraft/versions/1.21.1-NeoForge/resourcepacks/`
2. 在游戏里启用这个资源包（标题：`Moving Light Block - custom cube template`）
3. 替换贴图：
   ```text
   assets/movinglightblock/textures/block/moving_light_custom.png
   ```
4. 如需自定义模型形状，编辑：
   ```text
   assets/movinglightblock/models/block/moving_light_custom.json
   ```
5. 如需自定义滚动方式，编辑：
   ```text
   assets/movinglightblock/rolling.json
   ```
6. 游戏内按 `F3 + T` 重载资源包

## 文件结构

```text
example-resourcepack/
  pack.mcmeta
  assets/movinglightblock/
    blockstates/moving_light_visual.json    # 固定文件名，不要改
    models/block/moving_light_custom.json   # 标准原版 block model
    textures/block/moving_light_custom.png  # 换成你自己的贴图
    rolling.json                            # 滚动轴 / 是否滚动 / 速度
```

`blockstates/moving_light_visual.json` 必须保持这个名字和路径，它指向 `movinglightblock:block/moving_light_custom` 模型。模型里的纹理路径可以改成你自己的命名空间。

完整的格式说明与限制见仓库根目录的 [CUSTOMIZATION.md](../CUSTOMIZATION.md)。
