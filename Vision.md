# Облачные вычисления

Мы разрабатываем минималистичный движок для распределённого выполения задач **Task** на наборе узлов **Node**, один из которых является координатором **Coordinator**. Движок будет работать в парадигме *task-parallel*, когда распределяются тяжёлые вычислительные задачи, работающие на относительно небольших наборах данных, а не *data-parallel*, в которой над огромным объёмом данных совершаются относительно нетяжёлые с точки зрения вычислений задачи. 
Пользователь пишет обычный Java-код, объявляет задачи, отправляет их на выполнение через клиентскую библиотеку **Client Library** и получает результат. Пользовательский код задачи может также отправлять задачи в кластер на исполнение. Таким образом можно реализовать подход **divide-and-concur**, когда большая задача дробится на более мелкие.
Для поднятия кластера будем использовать **docker compose**.
## Пример
Вычисление числа Pi методом Монте-Карло. Пользовательский код.
```java
public final class PiTask implements Task<Long> {
  private final long n;
  private final long threshold;

  public PiTask(long n, long threshold) {
    this.n = n;
    this.threshold = threshold;
  }

  @Override
  public Long run(RemoteSystem rs) throws Exception {
    if (n <= threshold) {
      return countHits(n);
    }

    long leftN = n / 2;
    long rightN = n - leftN;

    // дробим задачу на две и запускаем распределённо
    RemoteRef<Long> a = rs.submit(new PiTask(leftN, threshold));
    RemoteRef<Long> b = rs.submit(new PiTask(rightN, threshold));

	// агрегируем результат
    return a.get() + b.get();
  }

  private static long countHits(long n) {
    long hits = 0;
    for (long i = 0; i < n; i++) {
      double x = Math.random();
      double y = Math.random();
      if (x * x + y * y <= 1.0) hits++;
    }
    return hits;
  }

  public static void main(String[] args) {
    RemoteSystem rs = RemoteSystem.connect("http://coordinator:8080");

    long N = 200_000_000L;
    long threshold = 10_000_000L;

    RemoteRef<Long> hitsRef = rs.submit(new PiTask(N, threshold));

    long hits = hitsRef.get();
    double pi = 4.0 * (double) hits / (double) N;

    System.out.println("pi ≈ " + pi);
  }
}
```
## Терминология
- **Task** - класс задачи с методом `R run(RemoteSystem rs)`, который выполняется удалённо
- **RemoteRef`<R`>** - ссылка на результат (future) с методом `R get()`, блокирующий до готовности результата
- **Driver** - пользовательский процесс, который подлючается к кластеру и запускает вычисление (первый вызов `submit`)
- **Coordinator** - центральный сервис: принимает задачи, распрелеляет их по **Worker**, хранит статусы и результаты
- **Node** - узел кластера (контейнер)
- **Worker** - процесс на **Node**, внутри которого исполняются задачи
- **Client Library** - библиотека для пользователя
## Схема запуска
**Driver** один раз собирает jar и отправляет его в **Coordinator**, который сразу же раздаёт этот jar всем **Node**. Дальше при запуске задач пересылаются только id этого jar и параметры вызова, а jar больше не отправляется. **Worker** берёт его из локального кэша и выполняет задачу. 
#### Отправка jar
```
Driver                 Coordinator     Node A/Worker     Node B/Worker
  |                          |               |               |
  |                          |               |               |
  |   upload(jar)            |               |               |
  |------------------------->|  upload(jar)  |               |
  |                          |-------------->|               |
  |                          |  upload(jar)  |               |
  |                          |------------------------------>|
  |                          |               |               |
```
#### Запуск Task
```
Driver                 Coordinator     Node A/Worker     Node B/Worker
  |                          |               |               |
  |                          |               |               |
  |   submit(T0)             |               |               |
  |------------------------->|  dispatch(T0) |               |
  |                          |-------------->| run(T0)       |
  |                          |   submit(T1)  |               |
  |                          |<--------------|               |
  |                          |  dispatch(T1) |               |
  |                          |------------------------------>| run(T1)
  |                          |   result(T1)  |               |
  |                          |<------------------------------|
  |                          |    get(T1)    |               |
  |                          |<--------------| combine       |
  |                          |   result(T0)  |               |
  |     get(T0)              |<--------------|               |
  |------------------------->|               |               |
```
#### Топология
```
┌───────────────────────────────┐
│             Driver            │
│        (Client Library)       │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│           Coordinator         │
└───────────────┬───────────────┘
                │
        ┌───────┴────────┐
        ▼                ▼
┌────────────────┐  ┌────────────────┐
│  Node A        │  │  Node B        │
│  Worker        │  │  Worker        │
│  jar cache     │  │  jar cache     │
└────────────────┘  └────────────────┘
```
