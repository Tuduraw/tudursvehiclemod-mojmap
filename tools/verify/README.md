# 検証キット(tvmtest)

Yarn版(1.1.1まで)とMojang版のjarを、同じ手順で動かして比べるためのものです。2026-10-31の移行時の検証(`claude/mojmap-verification-2026-10-31.md`)で使いました。どちらか一方のjarだけを動かして、全機種・全武装の動作確認に使うこともできます。Linux(bash)で動かすことを前提にしています。

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

`-PtvmJar`を省略すると`../../../build/libs/tudursvehiclemod-1.1.1-fabric-1.21.11.jar`を参照します。コンパイルに使うだけなので、どちらのjarに対してビルドしても同じハーネスになります。

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
| `/tvmtest stress <名前> <セット数> <tick数> [fire\|manned\|idle]` | 負荷の計測(下記) |
| `/tvmtest carriers <名前> <隻数> <tick数> park\|sail` | 空母(滑走路のある艦)の負荷の計測(下記) |
| `/tvmtest deck <名前>` | 甲板に防具立て・ゾンビ・ボート・豚を落とし、静止位置を記録 |
| `/tvmtest deckwalk setup\|board\|sail\|report` | クライアントの甲板歩行テスト用(下記) |
| `/tvmtest dmgscale setup` → `/tvmtest dmgscale <名前>` | ダメージ・爆発の倍率(サーバー設定)を6通りに切り替え、ハスクへの命中・石ブロック上の爆発(威力3)で、体力・ノックバック速度・壊れたブロック数・火の数を記録。setupで(3000, 3000)付近を強制ロードしてから数秒待って実行。終了後は倍率を1に戻す(ファイルには保存しない) |
| `/tvmtest speedtest setup` → `/tvmtest speedtest <名前>` | 全車両の最高速度(倍率込み、km/h)を記録。偽プレイヤーは座席に乗れないため、実際の走行は下の`speeddrive`で確認 |
| `/tvmtest setspeed <倍率>` | 車両の速度倍率をメモリ上だけ変更(ファイルには保存しない)。接続中のクライアントへの送信の確認用 |
| `/tvmtest setspeedtype <種類> <倍率>` | 種類ごとの速度倍率をメモリ上だけ変更(0で項目を消して共通に戻す)。キーはファイルと同じ書き方 |
| `/tvmtest destroyride <プレイヤー名> "<車両ID>" <高さ>` | そのプレイヤーを新しい車両に乗せ、40tick後に破壊し、その後200tickのあいだ搭乗し続けているかを20tickごとにログに出す(`-Dtvmtest.speedsync=1`のクライアントで接続しておく) |
| `/tvmtest takeoff <プレイヤー名> "<艦ID>" "<機体ID>" [sail]` | (-3200, 40)の水路に艦を浮かべ、60tick後に最初の滑走路の後端へ機体を置いてプレイヤーを乗せる。`-Dtvmtest.speedsync=1`のクライアントが200tick全速前進し、速度・甲板からの高さ・体力を5tickごとにログに出す。`sail`では艦が0.2ブロック/tickで前進しつつ±0.3ブロック上下する。プレイヤーがいなければ駐機のみ。`/tvmtest takeoffx <x>`で置く位置を左右にずらせる |
| `/tvmtest farveh <プレイヤー名>` | プレイヤーを(-3020, 0)に東向きで立たせ、200ブロック先(-2820)に旋回する航空機(脚を格納、スロットル0.8)・空中に止まるヘリ・地上のハーフトラックを、20ブロック先にハーフトラックを置き、30tickごとに(-2840, -40)の上空からミサイルのモデルの弾を+Z方向へ撃つ(遠方のチャンクは強制ロード)。サーバーの描画距離を4程度、`farVehicleSyncEnabled`をオンにして使う。`-Dtvmtest.farveh=1`のクライアントは遠方の機体の描画をオン・霧オフ・描画距離2にし、40tickごとに遠方の機体(エンジン音の強さ`engine=`、脚のある機体はその開閉の進み具合、1で格納)を記録、400・600tickでスクリーンショット、700tickで終了(`-Dtvmtest.farveh.off=1`で描画オフの比較)。サーバーは120tickごとに、遠方のハーフトラックの発射音を受け取るプレイヤー数を`FARVEH farListeners=`として出す(1になること)。320tick後にプレイヤーを(-2860, -20)へ移し、描画距離内に入った航空機の脚の進み具合を、クライアントが最初の1・3・10・30tickに`FARVEH real`として出す(旋回機は最初から1.0のまま変わらないこと) |
| `/tvmtest speeddrive <プレイヤー名>` | そのプレイヤーを(-3000, 0)のハーフトラックの運転席に乗せる。`-Dtvmtest.speedsync=1`のクライアントが200tick全速前進し、クライアント側での最高速度と倍率をログに出す |
| `/tvmtest carmob <プレイヤー名> "<車両ID>"` | そのプレイヤーを(-3000, 0)の新しい車両に乗せ、12・22・32ブロック先にAIなしのゾンビを置く(`-Dtvmtest.speedsync=1`のクライアントが全速前進)。260tick後に車両の体力とゾンビの体力を`CARMOB end`としてログに出す。`weight_type`が`tank`の車両は体力が減らないこと。航空機を指定し、クライアントを`-Dtvmtest.standby=1`で起動すると、20tick目に待機モードへ切り替えてから全速を入れ、50tickごとに`STANDBY standby= throttle= moved=`を出す(スロットル0.3以下・ほぼ動かないこと)。`-Dtvmtest.runs=<回数>`でクライアントが終了するまでの走行回数を変えられる |
| `/tvmtest avoid <on\|off> "<障害物の機体ID>" "<航空機ID>" <高さ>` | (-2600, 0)に障害物の機体を置き、その中を通るCASの経路で航空機を飛ばす(`autopilotCollisionAvoidance`をon/offに切り替える)。通過後に`AVOID … contactTicks minCentreDistance passHeight health`をログに出す。onでは接触0になること |

## 負荷の計測

`/tvmtest stress <名前> <セット数> <tick数> [モード]`は、全定義の機体を`セット数`台ずつ格子状に並べ、サーバーの1tickの所要時間(`START_SERVER_TICK`〜`END_SERVER_TICK`)を計測して`tvmtest-<名前>.json`に出力します(平均・中央値・95%点・最大)。2つのjarの処理の重さを比べるためのものです。

| モード | 内容 |
|---|---|
| `fire`(既定) | 全座席にダミーパイロットを乗せ、標的を置き、全武装を撃ち続ける |
| `manned` | ダミーパイロットと標的だけ(発射はダミーパイロット任せ) |
| `idle` | 無人の機体を並べるだけ |

- 前回の機体が残っていると結果が変わるため、毎回ワールド(`world/`)を消してから実行してください
- `fire`は乱数の影響が大きいので、複数回実行して比べてください
- どこが重いかは、サーバーを`-XX:StartFlightRecording=filename=x.jfr,settings=profile`付きで起動し、`jfr print --events jdk.ExecutionSample x.jfr`の`Server thread`の標本を集計すると分かります

## 空母の負荷計測

`/tvmtest carriers <名前> <隻数> <tick数> park|sail`は、滑走路を持つ艦の定義ごとに`隻数`隻を、それぞれ専用の水路に浮かべ、甲板にゾンビ3体・防具立て3体を乗せて計測します。`sail`では`ExternalMotionController`で各艦を0.3ブロック/tickで前進させ、左右8°で振らせます。結果には1tickの所要時間と、甲板に残った防具立ての数(`standsOnDeck`)が入ります。

## 甲板歩行テスト(クライアント)

`-Dtvmtest.deck=1`を付けてクライアントを起動すると、空母の甲板の上で、停泊中に30tick、航行中に40tick前進し、プレイヤーの甲板からの高さと歩いた距離(`walkParkedBlocks`・`walkSailingBlocks`)を`tvmtest-deckwalk.json`に書き出して終了します。

- 環境変数`TVM_SERVER=host:port`を付けると、シングルプレイの代わりにそのサーバーへ接続します。専用サーバーでは移動の検証(`moved wrongly`など)が有効になるため、甲板の運搬はこちらで確認してください
- 専用サーバー側では、`Tester`(オフラインUUID)をOPにし、サーバーとクライアントに同じパックを入れておく必要があります
- サーバー側の記録は`tvmtest-deckwalk-server.json`に出力されます

`scripts/run_server.sh`は、環境変数`JVMARGS`の内容をサーバーのJVM引数に加えます。

## 注意

- Fabric APIの`FakePlayer`は乗り物に乗れないため、サーバー側の操縦は対象外です。走行は実プレイヤー(クライアント)で確認します。
- 召喚直後の武装は弾が空なので、ハーネスがリフレクションで弾倉と燃料を満たします。対象は本体自身のフィールド`weaponAmmo` / `weaponReserveAmmo` / `FUEL`です。名前を変えた場合はハーネスも直してください。
- 空ワールドが無人で一時停止しないよう、`pause-when-empty-seconds=0`にしています。
