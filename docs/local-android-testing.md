# Local Android testing

## Backend
```bash
cd backend-api
cp .env.example .env
npm install
npm run db:migrate
npm run db:seed
npm run dev
```

Проверка backend:
```bash
curl http://localhost:3000/health
```

Проверка resolve-token:
```bash
curl -X POST http://localhost:3000/api/v1/config/resolve-token \
  -H "Content-Type: application/json" \
  -d '{"token":"demo-token"}'
```

## Android emulator
```bash
cd android-client
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"
```

## Physical Android
1. Узнать IP компьютера в локальной сети.
2. Указать backend URL: `http://LOCAL_PC_IP:3000`.
3. Убедиться, что телефон и ПК в одной Wi-Fi сети.
4. Проверить firewall на порт 3000.
5. Собрать APK.
6. Установить APK.
7. Открыть `zoootconf://demo-token`.
