
# E-Commerce Platform (Electronics & Appliances)

A backend e-commerce platform built with **Spring Boot**, **PostgreSQL**, **Redis**, and **Kafka**, focused on a tight, realistic slice of e-commerce functionality rather than trying to do everything.

## Problem

Most portfolio e-commerce projects are CRUD-only: a database and a REST API, nothing else. That doesn't reflect how real backend systems are built, and it doesn't give an interviewer anything to dig into. This project deliberately scopes down the *product* surface (electronics & appliances only, no reviews, no search, no payment gateway) in order to scope *up* the systems-design surface: caching strategy, an in-memory data store used as a primary store (not just a cache), and event-driven decoupling between the checkout path and its side effects.

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full request-flow diagram and component breakdown.

**In short:**
- Spring Boot REST API backed by PostgreSQL for durable data (products, categories, users, orders)
- Redis used two ways: as a cache in front of product listings, and as the primary store for the shopping cart
- Kafka used to decouple the checkout request from its side effects (order confirmation, inventory update)

## Features

### Auth
- Register / login with JWT

### Browsing
- `GET /products`
- `GET /products/{id}`
- `GET /products?category=`

### Cart (Redis-backed)
- Add to cart
- Remove from cart
- View cart

### Checkout
- `POST /orders` — creates an order, decrements stock, publishes an `OrderPlacedEvent` to Kafka

### Order history
- `GET /orders/me`

## Why Redis

Redis is used for two distinct purposes, not one:

1. **Product listing cache** — `GET /products` and `GET /products?category=` use a cache-aside pattern: check Redis first, on a miss read from PostgreSQL and write the result back to Redis with a TTL. This is invalidated whenever stock is updated, so cached listings don't drift too far from real inventory. It's a classic read-heavy caching case: product listings are read far more often than they change.
2. **Cart as primary store, not a cache** — the cart is *not* modeled as a `cart_items` table in Postgres at all. It lives in Redis, keyed by user ID. Carts are ephemeral, high-write, and don't need the durability or query guarantees of a relational table — Redis is a better fit for that access pattern than a database table would be. This is also the more interesting interview talking point: "why did you not put this in the database," rather than "I added a cache."

## Why Kafka

`POST /orders` (checkout) needs to be fast and needs to succeed reliably, but "send a confirmation" and "update inventory count" don't need to block the response back to the user. Checkout publishes an `OrderPlacedEvent`, and separate consumer listeners handle:
- Order confirmation (mocked "email sent")
- Inventory count update

This is the decoupling story: the checkout request path doesn't wait on notification or inventory logic, and those concerns can fail, retry, or scale independently of the main request path.

## Trade-offs considered

- **Cart in Redis vs. a `cart_items` table** — chose Redis for write-heavy, ephemeral access patterns; trade-off is that cart data isn't part of the relational data model and needs its own backup/expiry strategy rather than relying on normal DB durability.
- **Cache-aside vs. write-through for product listings** — chose cache-aside because product reads vastly outnumber writes, and staleness for a short TTL window is an acceptable trade-off for simplicity, versus keeping the cache always in sync on every write.
- **Kafka for two side effects vs. calling them synchronously** — chose async decoupling so a slow email/notification step can't slow down or fail checkout; trade-off is added infrastructure (a broker, at-least-once delivery/idempotency concerns) versus a simpler synchronous call chain.
- **Scope cuts** — payment gateway integration, reviews/ratings, search, an admin dashboard UI, and product variants were explicitly left out to keep the project buildable and demoable in the sprint window, in favor of depth on caching and eventing over breadth of features.

## Tech stack

- Java / Spring Boot
- PostgreSQL
- Redis
- Kafka
- JWT auth
- JUnit 5 + Mockito (unit tests)
- Testcontainers (integration test)

## Current status

- [x] Core MVP (auth, product browsing, cart, checkout, order history)
- [x] Redis: product listing cache (cache-aside) + cart storage
- [x] Kafka: `OrderPlacedEvent` producer + consumer(s) for confirmation/inventory update
- [x] One integration test
- [ ]Full test suite (unit + Testcontainers coverage across services)
- [x] Dockerized full stack (Postgres + Redis + Kafka + app in one `docker-compose.yml`)
- [ ] Live deployment

## How to run it locally

> Update this section with your actual setup once Docker Compose is wired up.

1. Clone the repo
2. Start dependencies (PostgreSQL, Redis, Kafka) — via `docker-compose up` once added, or run each locally
3. Set environment variables / `application.yml` for DB, Redis, and Kafka connection details
4. `./mvnw spring-boot:run`
5. API available at `http://localhost:8080`

## Next steps

- Finish Docker Compose so the whole stack (Postgres + Redis + Kafka + app) starts with one command
- Expand test coverage (more unit tests, more Testcontainers integration coverage)
- Deploy a live demo (Railway/Render)
- Write up the Redis-cart-vs-table decision as a standalone technical post