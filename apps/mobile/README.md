# Nanas' Kitchens Mobile

Kotlin Multiplatform + Compose Multiplatform buyer app for Android and iOS, sharing UI and domain code. The desktop target is a development preview.

The mobile app mirrors `apps/web` and uses the Spring Boot API at `http://localhost:8080`. If the API is unavailable, nearby kitchens and menus fall back to realistic demo data so the full prototype remains explorable.

## Included flows

- Nearby kitchens, cuisine filters, trust and inventory badges
- Kitchen profile and today's live-style menu
- Cart, pickup/delivery choice, ready-time selection and demo confirmation
- Orders, AI chat, seller invitation and profile tabs

## Run

```bash
./gradlew :composeApp:run
```

Open the project in Android Studio for Android. The generated `NanasKitchensMobile` framework exposes `MainViewController()` for an iOS host application.
