# MOJMAP.md - Mojang公式マッピングへの移行

1.21.11 Fabric版は、**Mojang公式マッピング**(`loom.officialMojangMappings()`)で開発しています。
1.1.1まではYarnマッピングで開発していました。このファイルは、移行の経緯と、
Yarn版向けに作られたアドオンMODとの互換性を保つために守ることをまとめたものです。

## 移行の経緯

- Minecraft 1.21.11は、FabricがYarnマッピングを更新する最後のバージョンです。以降はMojang公式マッピングが推奨されています。
- Forge 1.20.1版などの他ローダー・他バージョンも、Mojang公式の名前でコードを共有できます
  (Forge版はこのプロジェクトのソースから自動生成しています)。
- 移行にあたっては、Yarn版(1.1.1)のソースを`tools/mojmap/convert.py`で機械的に変換し、
  両方のjarを中間名(intermediary)の段階でメンバー単位に比較して、実行時のコードが同じであることを確認しました
  (下記「Yarn版との同等性」)。

| 項目 | 1.1.1まで(Yarn) | 現在(Mojang) |
|---|---|---|
| マッピング | Yarn `1.21.11+build.4` | Mojang公式 |
| jar名 | `tudursvehiclemod-1.1.1.jar` | `tudursvehiclemod-1.1.1-fabric-1.21.11.jar` |
| Mavenの座標 | `com.example.tudursvehiclemod:tudursvehiclemod:1.1.1` | `com.example.tudursvehiclemod:tudursvehiclemod:1.1.1-fabric-1.21.11` |
| `fabric.mod.json`のバージョン | `1.1.1` | `1.1.1`(変わらない) |
| mod ID・パッケージ・クラス名 | `tudursvehiclemod` | 同じ |

jar名とMavenのバージョンには、ローダー名とMinecraftのバージョンを付けています(`<mod_version>-<mod_loader>-<minecraft_version>`、
`gradle.properties`の`mod_loader`と`minecraft_version`から作られます)。複数の環境向けのjarを区別するためです。
MODのバージョン(`fabric.mod.json`、アドオンの`depends`が比較する値)は`mod_version`のままです。

## アドオンMODとの互換性

アドオンMODは前提MODのjarを中間名で参照するため、アドオン側のマッピングがYarnでもMojangでも動作します。
Yarn版(1.1.1)に対して作られた既存のアドオンも、そのまま動作します。そのために、次の点を守ってください。

### 総称型オーバーライドの名前(転送用メソッド)

描画クラスの`updateRenderState(AbstractVehicleEntity, VehicleRenderState, float)`のように、総称型の型引数で宣言した
オーバーライドは、jarの中でも中間名にならず、**ソースに書いた名前のまま**残ります(中間名になるのは、Minecraftが呼ぶ
ブリッジメソッドだけです)。Yarn版のjarではこれらがYarn名で、アドオンMODはその名前を直接呼んでいます
(例: mechframeの`MechRenderer`が`super.updateRenderState(...)`を呼ぶ)。

そのため、本体のメソッドは**Yarn名のまま残し**、Mojang名のオーバーライドからそこへ転送しています。

| クラス | Yarn名(本体) | Mojang名(転送) |
|---|---|---|
| `VehicleEntityRenderer` | `updateRenderState`・`render`・`getPositionOffset` | `extractRenderState`・`submit`・`getRenderOffset` |
| `VehicleProjectileRenderer` | `updateRenderState`・`render` | `extractRenderState`・`submit` |
| `DummyPilotEntityRenderer` | `updateRenderState`・`getTexture`・`hasLabel` | `extractRenderState`・`getTextureLocation`・`shouldShowName` |
| `DummyPilotModel` | `setAngles` | `setupAnim` |

- **Yarn名のメソッドを削除・改名しないでください。** Yarn版向けのアドオンが`NoSuchMethodError`で停止します。
- 処理を変更するときは、Yarn名の本体を変更します。転送メソッドは本体を呼ぶだけにしておきます。
- 同じ形のオーバーライドを新しく追加する場合は、Mojang名だけで構いません(既存のアドオンが呼んでいないため)。

### 公開しているメソッド・フィールド

アドオンが使う`public`・`protected`のメンバー(`tudursvehiclemod$`で始まるものなど)は、名前と引数の型を変えると
既存のアドオンが動かなくなります。変える場合は、古いものを残して新しいものへ転送するか、バージョンを上げて
`docs/Readme_Addon_Mod.md`に記載してください。

## Yarn版との同等性

移行時に、Yarn版とMojang版のjarを比較して、次のことを確認しています(`tools/mojmap/compare_jars.py`)。

- クラス・リソースの構成が完全に一致(`fabric.mod.json`、Mixin設定、テクスチャ等)
- Yarn版の全メンバーが、Mojang版にも同じ名前・同じバイトコードで存在
- 違いは、ラムダの合成メソッド名(privateで、外から呼ばれない)と、上記の転送用メソッドだけ

既存のアドオンとの互換性を確かめたいときは、Yarn版1.1.1のjarと今のjarをこのスクリプトで比べると、
Yarn版にあったメンバーが欠けていないかを確認できます。その後に変更したメソッドも「中身が違う」として
報告されるため(`code differs`)、見るのは`missing in Mojang jar`・`class only in Yarn jar`と報告されたものです。
報告は先頭40件までなので、多いときは変更の少ない時点のjarと比べてください。

```
python tools/mojmap/compare_jars.py <Yarn版1.1.1のjar> <今のjar> <作業フォルダ> --yarn <yarnのmappings.tiny> --mojmap <mojmapのmappings.tiny>
```

## 移行に使ったツール(通常は使いません)

| ファイル | 内容 |
|---|---|
| `tools/mojmap/convert.py` | Yarn版のプロジェクトからこのプロジェクトを作り直す。**実行すると`src/`とビルドファイルがYarn版の内容で置き換わり、こちらで加えた変更が失われます。** Yarn版の開発を終えたため、今後は使いません |
| `tools/mojmap/fix_leftovers.py`・`manual_renames.json`・`mapping_lookup.py` | `convert.py`の補助。`mapping_lookup.py`は、YarnとMojangの名前の対応を調べるのに今も使えます |
| `tools/mojmap/compare_jars.py` | 2つのjarを中間名でメンバー単位に比較する(上記) |
| `tools/verify/` | Yarn版とMojang版を実際に動かして比べる検証キット(`tools/verify/README.md`) |

YarnとMojangの名前の対応を調べるには:

```
python tools/mojmap/mapping_lookup.py <yarnのmappings.tiny> <mojmapのmappings.tiny> "Entity#getPitch"
```

mappings.tinyの場所: `~/.gradle/caches/fabric-loom/1.21.11/` の
`net.fabricmc.yarn.*/mappings.tiny`(Yarn)と `loom.mappings.*layered*/mappings.tiny`(Mojang)

## 残っているYarnの名残

機械的な変換では、次のものは変換されていません。コードの動作には関係ありません。

- コメント・Javadoc内のメソッド名(`getYaw()`など)
- 引数・ローカル変数の名前(`world`など)

編集のついでに、Mojang名(`getYRot()`・`level`など)へ直していってください。上記の転送用メソッド(Yarn名の本体)は、
互換性のために名前を残しているものなので、直さないでください。
