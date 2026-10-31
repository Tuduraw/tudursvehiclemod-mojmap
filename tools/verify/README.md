# 検証キット(tvmtest)

公開版(Yarn)とMojang版のjarを、同じ手順で動かして比べるためのものです。2026-10-31の検証(`claude/mojmap-verification-2026-10-31.md`)で使いました。Linux(bash)で動かすことを前提にしています。

## 構成

| 場所 | 内容 |
|---|---|
| `harness/` | 検証用mod `tvmtest`。公開版と同じYarnマッピングでビルドし、アドオンmodと同じ立場で本体を呼ぶ |
| `scripts/setup_server.sh` | 本番構成のテストサーバーを作る(Fabric API・パック・ハーネスを配置、RCON有効) |
| `scripts/run_server.sh` | サーバーでのシナリオ実行 → 全機を並べて保存 → 再起動 → 再読み込み |
| `scripts/rcon.py` | 最小のRCONクライアント |
| `scripts/compare_reports.py` | 結果JSON同士の差分 |
| `client/launch.py` | 画面なし(Xvfb)でクライアントを起動する最小ランチャー |
| `client/options.txt` | クライアント用の設定(描画距離6、音量0、初回案内なし) |

## ハーネスのビルド

```
cd harness
./gradlew build -PtvmJar=<比べるtudursvehiclemodのjar>
```

`-PtvmJar`を省略すると`../../../build/libs/tudursvehiclemod-mojmap-1.1.0.jar`を参照します。コンパイルに使うだけなので、どちらのjarに対してビルドしても同じハーネスになります。

## サーバーでの比較

作業フォルダ(既定は`tools/verify/work`、環境変数`VT`で変更可)に次を置きます。

- `dl/fabric-server-launch.jar`(meta.fabricmc.netの1.21.11/0.19.3用サーバーランチャー)
- `dl/fabric-api-<版>.jar`
- `packs/`(テストに使うアドオンパック。例: FictionalWW2Pack、`tools/sample_pack_generator`で作ったsample_pack)

```
scripts/setup_server.sh work/srv-yarn <Yarn版jar> 0
scripts/setup_server.sh work/srv-moj  <Mojang版jar> 10   # 2台同時ならポートをずらす
scripts/run_server.sh work/srv-yarn yarn1
scripts/run_server.sh work/srv-moj  moj1
python3 scripts/compare_reports.py work/srv-yarn/tvmtest-yarn1.json work/srv-moj/tvmtest-moj1.json
```

`LIMIT=3 scripts/run_server.sh ...`で、先頭3機だけに絞れます。

結果はサーバーフォルダに出力されます。

- `tvmtest-<名前>.json`: 機体ごとの待機・前進・全武装の無人発射(標的への被害・弾数)・ダミーパイロット搭乗
- `-park.json` / `-parkcheck.json`: 再起動の前後の状態
- `log-run.log` / `log-restart.log`: サーバーログ

乱数(弾のばらつき、爆発)で回ごとに値が揺れます。各jarで2回ずつ実行し、jar間の差が回ごとの揺れの範囲に収まるかで判断してください。

## クライアントでの比較

```
python3 client/launch.py prepare      # ライブラリ・client.jar・アセット(効果音を除く)を取得
```

ゲームフォルダを用意します。

- `mods/`: Fabric API、本体、ハーネス
- `tudursvehiclemod-addons/`: 比較に使うパック
- `saves/tvm/`: 平坦ワールド
- `options.txt`

起動:

```
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" \
  python3 client/launch.py run <ゲームフォルダ> -Dtvmtest.gallery=1
```

起動するとワールドに入り、次の順で自動的に進んで終了します。

1. `/tvmtest gallery`: 全機をダミーパイロット搭乗状態で並べる
2. 1機ずつ撮影: `screenshots/tvm_NN_a.png`(通常時)、`_b.png`(発射直後)
3. 実プレイヤーとして右クリックで搭乗し、前進100tick+発射20tick。撮影: `_c.png`
4. 結果を`tvmtest-ride.json`に出力

## コマンド一覧(サーバーコンソール/RCON)

| コマンド | 内容 |
|---|---|
| `/tvmtest run <名前> [台数]` | サーバーシナリオ |
| `/tvmtest park <名前>` / `parkcheck <名前>` | 再起動前後の記録 |
| `/tvmtest gallery` / `view <i>` / `fire <i>` / `ride <i>` / `rideend <i>` | クライアント用 |
| `/tvmtest probe` | 搭乗・発射の簡易確認 |

## 注意

- Fabric APIの`FakePlayer`は乗り物に乗れないため、サーバー側の操縦は対象外です。走行は実プレイヤー(クライアント)で確認します。
- 召喚直後の武装は弾が空なので、ハーネスがリフレクションで弾倉と燃料を満たします。対象は本体自身のフィールド`weaponAmmo` / `weaponReserveAmmo` / `FUEL`です。名前を変えた場合はハーネスも直してください。
- 空ワールドが無人で一時停止しないよう、`pause-when-empty-seconds=0`にしています。
