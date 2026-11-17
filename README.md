# ClipForge

แอปตัดต่อวิดีโอสำหรับ Android (Kotlin + Jetpack Compose + Media3) ออกแบบให้ทำงานบนเครื่องเป็นหลัก

## สถานะ: Sprint 2 (โมเดลและเทสผ่าน, UI และ Media3 ยังไม่ได้คอมไพล์)

**ตรวจแล้ว (JVM, Kotlin 2.0.21)**
- [x] โมเดล Timeline ทั้งหมด คอมไพล์และผ่าน 32 unit test
  - Track แบบ magnetic (หลังลบ/ตัด คลิปด้านหลังเลื่อนตาม) และ overlay (วางได้อิสระ มีช่องว่างได้)
  - split, trim, delete, move, place, ปรับสี, เพิ่มคลิป/แทร็ก, undo/redo
  - ค่า `ColorAdjust` อยู่ในช่วง -1..1 และถูกตรวจตอนสร้าง

**เขียนแล้ว แต่ยังไม่ได้คอมไพล์ (ต้องมี Android SDK)**
- [x] ไทม์ไลน์หลักและไทม์ไลน์ซ้อน วางตำแหน่งคลิปด้วย `startOf()` ทั้งสองแบบ
- [x] เพิ่มคลิปหลัก (ต่อท้าย) และเพิ่มคลิปซ้อน (วางที่หัวเล่น)
- [x] แยกคลิป, ลบคลิป, Undo/Redo (ทุกการแก้ไขผ่าน `commit()` ทางเดียว)
- [x] สไลเดอร์ความสว่าง คอนทราสต์ ความอิ่มสีของคลิปที่เลือก
- [x] พรีวิวไทม์ไลน์หลักด้วย ExoPlayer playlist และ proxy 360p
- [x] ส่งออก track หลักพร้อมปรับสีผ่าน Media3 `Effects`

**ยังไม่ได้ทำ**
- [ ] พรีวิวคลิปซ้อนและส่งออกคลิปซ้อน (ต้องใช้ `Composition` หลาย sequence หรือ `OverlayEffect`)
- [ ] พรีวิวสีแบบเรียลไทม์ (ตอนนี้สีมีผลเฉพาะตอนส่งออก)
- [ ] ทรานซิชัน, ข้อความ, เสียง (ระดับเสียงรายคลิป), ความเร็ว
- [ ] บันทึกโปรเจกต์ (Room)
- [ ] วัดเวลาส่งออกและการใช้แรมบนเครื่องอ้างอิงหนึ่งรุ่น

**ต้องตรวจเมื่อคอมไพล์ฝั่ง Android**
- ชื่อคลาสและช่วงค่าของ Media3 ใน `TimelineExporter.colorEffects()`: `Brightness`, `Contrast`, `HslAdjustment.adjustSaturation` เขียนจากเอกสาร ยังไม่ได้ตรวจกับไลบรารีจริง
- Compose `Slider`, `horizontalScroll` และการวาง `offset` ของแต่ละคลิป

## การตัดสินใจ

- **Android อย่างเดียว** ในเฟสนี้
- **AI ทำบนเครื่อง (on-device)** ตามแผนเฟส AI:
  - คำบรรยายอัตโนมัติ: whisper.cpp ผ่าน JNI หรือโมเดล speech ขนาดเล็ก
  - ตัดพื้นหลัง: MediaPipe selfie / image segmentation
  - ตัดฉากอัตโนมัติ: วิเคราะห์ histogram และการเปลี่ยนภาพบนเฟรมย่อ
- **ไม่คัดลอก** แบรนด์ ไอคอน เทมเพลต เพลง หรือเอฟเฟกต์ของ CapCut
- **พรีวิวใช้ proxy, ส่งออกใช้ต้นฉบับ** เพื่อให้ตัดต่อลื่นโดยไม่เสียคุณภาพไฟล์ที่ส่งออก

## โครงสร้าง

```
app/src/main/java/dev/clipforge/
  MainActivity.kt            จุดเข้าแอป
  timeline/Timeline.kt       Clip, Track (magnetic/overlay), ColorAdjust, TimelineOps, EditHistory   (ไม่พึ่ง Android)
  timeline/PlayheadOps.kt    locate(), splitTimelineAt(), appendToMainTrack()                        (ไม่พึ่ง Android)
  editor/EditorState.kt      สถานะที่ Compose สังเกตได้ ทุกการแก้ไขผ่าน commit()
  media/MediaInfo.kt         อ่านความยาววิดีโอ
  media/ProxyGenerator.kt    สร้าง proxy พรีวิว
  export/TimelineExporter.kt ส่งออก track หลักด้วย Composition และ Effects
  ui/EditorScreen.kt         หน้าจอ: พรีวิว, ไทม์ไลน์หลัก/ซ้อน, สไลเดอร์สี, ปุ่มเครื่องมือ

app/src/test/java/dev/clipforge/timeline/
  TimelineTest.kt
  PlayheadOpsTest.kt
  OverlayTest.kt
```

## การรัน

```bash
# ต้องมี Android SDK (ANDROID_HOME) และ JDK 17
./gradlew :app:testDebugUnitTest   # unit test ของโมเดล Timeline
./gradlew :app:installDebug        # ติดตั้งบนเครื่องจริง
```

> หมายเหตุ: ยังไม่ได้สร้าง Gradle wrapper ให้รัน `gradle wrapper --gradle-version 8.9` หนึ่งครั้ง หรือเปิดโปรเจกต์ใน Android Studio

## ข้อจำกัดที่ทราบ

- พรีวิวและส่งออกใช้เฉพาะ track หลัก คลิปซ้อนจะแสดงบนไทม์ไลน์เท่านั้น
- ถ้าเพิ่มคลิปเร็ว ๆ หลายไฟล์ จะสร้าง proxy หลายตัวพร้อมกัน ควรทำคิวงาน
- ยังไม่ได้จัดการ lifecycle (เช่น หยุดเล่นเมื่อแอปไปพื้นหลัง)
- สไลเดอร์สีสร้างหนึ่งรายการใน undo ต่อการลากหนึ่งครั้ง ควรรวมเป็นการลากเดียวในขั้นต่อไป
