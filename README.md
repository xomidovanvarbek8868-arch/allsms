# allSMS Sender — Android ilova (qurilma tomoni)

Bu ilova allSMS kabinetidagi **Qurilmalar** bo'limida SMS jo'natish uchun ishlatiladi.
Telefonga o'rnatilgach, u kabinetdan kelgan xabarlarni haqiqiy SIM-karta orqali yuboradi.

## Qanday ishlaydi

1. Kabinetda **Qurilmalar → Qurilma qo'shish** tugmasini bosasiz — 6 xonali kod chiqadi.
2. Shu ilovada server manzili + kodni kiritib **Ulash** tugmasini bosasiz.
3. Ilova SMS yuborish ruxsatini so'raydi — ruxsat bering.
4. Shundan keyin ilova fonda ishlab turadi: ~6 soniyada bir marta serverdan "yuborilishi
   kerak bo'lgan SMS bormi" deb so'raydi, bo'lsa — telefon SIM-kartasi orqali haqiqatan
   yuboradi va natijani serverga qaytaradi.
5. Kabinetdagi **Telefon orqali yuborish** endi shu qurilma haqiqatan ishlab turgandagina
   ishlaydi (oldingi versiyadagi soxta "simulyatsiya" olib tashlandi).

## Build qilish — 2 usul

### A) Android Studio bilan (eng oson)
1. Android Studio (so'nggi versiya) o'rnating — https://developer.android.com/studio
2. **File → Open** → shu `android-app` papkasini tanlang.
3. Gradle sinxronizatsiyasi tugashini kuting (internet kerak, ~1-3 daqiqa).
4. Telefoningizni USB orqali ulang (Developer options → USB debugging yoqilgan bo'lsin)
   yoki emulyator ishlating, so'ng yashil **Run ▶** tugmasini bosing —
   yoki **Build → Build Bundle(s)/APK(s) → Build APK(s)** orqali `.apk` fayl oling
   (natija: `app/build/outputs/apk/debug/app-debug.apk`).
5. Shu APK faylni istalgan Android telefonga o'tkazib o'rnatishingiz mumkin
   ("Noma'lum manbalardan o'rnatish"ga ruxsat bering).

### B) Android Studio'siz — GitHub Actions orqali (kompyuteringizga hech narsa o'rnatmasdan)
1. Shu `android-app` papkani GitHub'ga (yangi repo) yuklang.
2. **Actions** bo'limiga o'ting — "Build APK" workflow avtomatik ishga tushadi
   (`.github/workflows/build-apk.yml` allaqachon tayyor).
3. Ishlab bo'lgach, run sahifasidagi **Artifacts** dan
   `allsms-sender-debug-apk` faylni yuklab oling — bu tayyor `.apk`.

> Eslatma: bu — **debug** build (sinov uchun to'liq yetarli, ruxsat so'raydi va ishlaydi).
> Play Store'ga chiqarish yoki keng tarqatish uchun keyinchalik **release** build va
> imzolash (signing key) kerak bo'ladi — kerak bo'lsa shuni ham sozlab beraman.

## Sozlash

- **Server manzili**: ilovada `http://IP:PORT` yoki `https://domeningiz.uz` kiritiladi.
  Agar backend hali localhostda bo'lsa, telefon o'sha kompyuter bilan bir Wi-Fi tarmog'ida
  bo'lishi va serverning lokal IP manzili ishlatilishi kerak (`http://192.168.x.x:3000`
  kabi) — production uchun haqiqiy domen + HTTPS tavsiya etiladi.
- Ulanganidan keyin ilova sozlamalari `Prefs.kt` orqali telefonda saqlanadi
  (server manzili, qurilma tokeni) — ilovani o'chirib-yoqsangiz ham saqlanib qoladi.

## Bilinigan cheklovlar (v1)

- Faqat standart SIM (default) orqali yuboradi — dual-SIM telefonlarda aniq SIM tanlash
  keyingi versiyada qo'shilishi mumkin.
- Yetkazilganlik (delivery report) emas, faqat "SIM radiosiga jo'natildi" holatini kuzatadi
  (Eskiz.uz integratsiyasi bilan bir xil mantiq — mavjud backend shu bo'yicha ishlaydi).
- Bitta hisobda faqat bitta qurilma bir vaqtda "band" bo'lib SMS oladi (birinchi online
  qurilma tanlanadi) — bir nechta telefonni bir vaqtda ishlatib yukni taqsimlash keyingi
  bosqich.
