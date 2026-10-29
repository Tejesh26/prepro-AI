# PREPRO AI - AI Personalized Timetable & Study Assistant

A production-ready, psychology-backed study planner and automated timetable generator built entirely within the **pure Java ecosystem** using **Spring Boot 3**, **Spring Security 6**, **Thymeleaf**, **H2 Database**, and the **Google Gemini AI API**.

---

## 🌟 Key Features

1. **AI Brain & Psychological Pacing**:
   - Priority allocation: **50% time** to weak (`POOR`) subjects, **30% time** to moderate (`AVERAGE`) subjects, and **20% time** to strong (`GOOD`) subjects.
   - Enforces strict cognitive science rules: **30-minute intense study blocks** followed by **5–10 minute cognitive breaks**.
   - Automatic model failover cascade across Gemini models (`gemini-3.5-flash`, `gemini-3.1-flash-lite`, `gemini-3-flash-preview`).

2. **Multi-Format Input Layer**:
   - **Syllabus PDF Extraction**: Apache PDFBox 3.x extracts chapters and topics from official coaching or syllabus documents.
   - **Real-Time Voice-to-Text**: Dictate study hours and constraints directly into the browser using speech recognition.
   - **Multimodal Audio AI Transcription**: Upload voice notes (`.wav`, `.mp3`, `.m4a`) to be transcribed via Gemini Multimodal API.

3. **Fixed Master Timetable Grid (1–3 Month Master Plan)**:
   - Modern 2D matrix ($X$-Axis: Chronological Time Slots, $Y$-Axis: Subjects, Category Rows).
   - Color-coded cells: 🟢 Study Blocks (30m), ☕ Cognitive Rest, 🟣 Live Zoom Classes with one-click **Join Meeting** links.
   - Interactive day switcher pills (Monday – Sunday) and linear chronological agenda view.

4. **Multi-Format Export Engine**:
   - **PDF Document**: Formatted paginated table grid with header styling (Apache PDFBox).
   - **Excel Spreadsheet (`.xlsx`)**: Color-coded workbooks with styled cells (Apache POI).
   - **CSV Export**: Clean spreadsheet format for calendar imports.

5. **Desktop Alarms & Auto-Launch**:
   - Native OS system tray alerts and audio beeps.
   - 10-minute early warning notifications.
   - Automatic Zoom link browser launcher.

6. **Enterprise Security & Auth**:
   - Spring Security 6 with CSRF protection.
   - BCrypt cryptographic password hashing.
   - Public student self-registration and isolated session management.

---

## 🛠️ Tech Stack (100% Pure Java Architecture)

- **Language**: Java 17+
- **Backend Framework**: Spring Boot 3.4.5 (Spring MVC, Spring Data JPA, Spring Security)
- **Frontend / Presentation**: Thymeleaf (Server-Side Rendering) + Bootstrap 5 (CDN)
- **Database**: Embedded H2 Database (File-based local persistence / PostgreSQL switchable)
- **Libraries**:
  - Apache PDFBox 3.0.2
  - Apache POI 5.2.5
  - Jackson Databind 2.17.1
- **Build Tool**: Apache Maven

---

## 🚀 Getting Started

### Prerequisites
- **JDK 17 or higher** (JDK 17 to JDK 26 compatible)
- **Apache Maven 3.8+** (or use included wrapper)

### Installation & Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/prepro-ai-study-planner.git
   cd prepro-ai-study-planner
   ```

2. **Set your Gemini API Key (Optional):**
   ```bash
   # Windows PowerShell
   $env:GEMINI_API_KEY="YOUR_GEMINI_API_KEY"

   # Linux / macOS
   export GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
   ```

3. **Build the project:**
   ```bash
   mvn clean package -DskipTests
   ```

4. **Run the executable JAR:**
   ```bash
   java -jar target/timetable.jar
   ```

5. **Access in your browser:**
   Open [http://localhost:8082](http://localhost:8082)

---



---

## 📄 License
This project is open-source under the MIT License.
