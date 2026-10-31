# tudursvehiclemod-mojmap(Mojangマッピングの開発用コピー)

公開版(Yarnマッピング、1.21.11 Fabric)から自動生成した開発用プロジェクトです。
**公開版には一切変更を加えていません。** 公開・配布は従来どおりYarn版から行います。

このコピーの用途は、他ローダー・他バージョンへの移植作業(`claude/forge-1.20.1-port-evaluation-2026-10-31.md`)の土台にすることです。
Forge 1.20.1や26.1以降と同じ公式名でコードを読み書きできます。

## Yarn版との関係

| 項目 | Yarn版(公開版) | このコピー |
|---|---|---|
| マッピング | Yarn `1.21.11+build.4` | Mojang公式(`loom.officialMojangMappings()`) |
| jar名 / Mavenのartifact | `tudursvehiclemod` | `tudursvehiclemod-mojmap` |
| mod ID・パッケージ・クラス名 | `tudursvehiclemod` | 同じ |
| 実行時のコード | — | 同じ(下記「同等性の確認」) |
| `docs/`・`official_addonpack/`・`tools/`の変換ツール類 | あり | 含めない(Yarn版を参照) |

- artifact名を分けているので、`publishToMavenLocal`してもYarn版(アドオンmodが参照するもの)を上書きしません。
- mod IDは同じなので、両方のjarを同時に`mods`へ入れるとFabric Loaderが重複として起動を止めます。

## 同等性の確認

アドオンmodは公開版(Yarn版)のjarに対してコンパイルされています。Mojang版はその代わりにそのまま入るよう、公開版jarのクラス・フィールド・メソッドを**すべて同じ名前・同じ中身で**持つ必要があります。Loomはどちらのjarも中間名(intermediary)で出力するので、`tools/mojmap/compare_jars.py`で両方を逆アセンブルし、メンバー単位で比較しています。

- クラス・リソースの構成: 完全に一致(`fabric.mod.json`、Mixin設定、テクスチャ等)
- 公開版の全メンバー: Mojang版にも同じ名前・同じバイトコードで存在
- 許容している違いは次の2つだけです。
  - ラムダの合成メソッド名(`lambda$reload$0` → `lambda$onResourceManagerReload$0`)。privateで、外から呼ばれることはありません。
  - Mojang版にだけある転送用メソッド9個(下記)。

### 総称型オーバーライドの名前(転送用メソッド)

描画クラスの`updateRenderState(AbstractVehicleEntity, VehicleRenderState, float)`のように、総称型の型引数で宣言したオーバーライドは、公開版のjarでも**Yarn名のまま**残ります(中間名になるのは、Minecraftが呼ぶブリッジメソッドだけです)。アドオンmodはこのYarn名を直接呼んでいます(例: mechframeの`MechRenderer`が`super.updateRenderState(...)`を呼ぶ)。

そのためMojang版では、本体のメソッドはYarn名のまま残し、Mojang名のオーバーライド(`extractRenderState`など)からそこへ転送する形にしています。対象は`VehicleEntityRenderer`(`getPositionOffset`・`updateRenderState`・`render`)、`VehicleProjectileRenderer`(`updateRenderState`・`render`)、`DummyPilotEntityRenderer`(`updateRenderState`・`getTexture`・`hasLabel`)、`DummyPilotModel`(`setAngles`)です。

## Yarn版の更新に追従する

`src/`は手で編集しません。Yarn版を更新したら、作り直します。

```
python tools/mojmap/convert.py --yarn <Yarn版プロジェクトのパス> --verify
```

1. Yarn版を`build/mojmap-convert/yarn-copy`にコピーし、LoomのmigrateClientMappingsでmainとclientのソースをまとめて変換します。Yarn版のフォルダには書き込みません。
2. このコピーのビルドファイルをYarn版から作り直します(マッピング行、jar名、プロジェクト名だけを変更)。`src/`を変換結果とYarn版のリソースで置き換えます。`tools/`とこのファイルは残ります。
3. `tools/mojmap/manual_renames.json`の手修正を適用します。
4. コンパイルし、javacのエラーをもとに`fix_leftovers.py`が残った名前を直します。コンパイルが通るまで繰り返します。
5. `--verify`を付けると、両方のjarを作って比較します。公開版にあるメソッドが欠けている・中身が違うなど、アドオンmodとの互換性を壊す違いがあれば、終了コード1で止まります。**配布・テストの前に必ず`--verify`を付けて実行してください。**

所要時間は数分です(初回はLoomのキャッシュ作成が加わります)。

### 自動で直らなかったとき

`fix_leftovers.py`が`UNRESOLVED`と表示した箇所は、`manual_renames.json`に1行ずつ追加します。どの名前に直すかは次で調べられます。

```
python tools/mojmap/mapping_lookup.py <yarnのmappings.tiny> <mojmapのmappings.tiny> "Entity#getPitch"
```

mappings.tinyの場所: `~/.gradle/caches/fabric-loom/1.21.11/` の
`net.fabricmc.yarn.*/mappings.tiny`(Yarn)と `loom.mappings.*layered*/mappings.tiny`(Mojang)

自動変換が取りこぼす主なパターン:
- ラムダの中のコード(クライアントのtick処理など)→ `fix_leftovers.py`が直す
- 総称型の引数を持つオーバーライド(描画クラスの`updateRenderState`など)→ `manual_renames.json`で、Yarn名の本体+Mojang名の転送メソッドにする(名前を付け替えるだけだとアドオンmodが`NoSuchMethodError`で落ちる)
- このmodのインターフェースが、Entityのメソッドをそのまま実装として使っている場合(`FreeCameraVehicle`)→ `manual_renames.json`
- `VertexFormat.DrawMode`のような入れ子クラスの参照 → `manual_renames.json`

### 既知の制限

- コメント・Javadoc内のメソッド名はYarnのまま残ります(`getYaw()`など)。コードの動作には関係ありません。
- 引数・ローカル変数の名前は変換しません(Yarn版のまま)。

## 動作の比較(tools/verify)

公開版とMojang版を実際に動かして比べる検証キットです。使い方は`tools/verify/README.md`を参照してください。
