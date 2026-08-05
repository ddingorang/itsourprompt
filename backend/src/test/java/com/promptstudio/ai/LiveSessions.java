package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;

import java.util.List;

/**
 * 실호출 하네스({@link LiveFeedbackHarnessTest})가 쓰는 손으로 만든 세션 픽스처.
 *
 * <p>로컬에 실데이터 DB가 없어 세션 다섯 개를 손으로 짰다. 다섯은 각각 다른 것을 재려고 있다 —
 * S1 정석, S2 범위 초과, S3 결정적 턴(파일에는 변경이 있는데 테스트가 계속 실패), S4 인프라 사고,
 * S5 퇴보(앞 턴이 통과시킨 테스트가 깨짐).
 *
 * <p>턴마다 기대 판정을 둘 들고 있다. {@code withoutSignal}은 실행 데이터 없이 파일만 보고 내릴 수 있는
 * 판정이고, {@code withSignal}은 턴별 채점 결과를 함께 실었을 때 맞는 판정이다. 둘이 갈리는 턴이
 * <b>결정적 턴</b>이고, 채점 결과 입력이 겨냥한 것이 정확히 그 턴이다.
 *
 * <p>{@link OpenAiFeedbackGenerator}가 패키지 프라이빗이라 같은 패키지에 둔다.
 */
final class LiveSessions {

    /** 판정 1의 고정 문장. 하네스는 응답에서 이 넷만 찾는다. */
    static final String AS_ASKED = "요청한 대로 바뀌었어요";
    static final String PARTIAL = "일부만 바뀌었어요";
    static final String NOT_IN_PROMPT = "요청이 프롬프트에 없었어요";
    static final String NOT_DONE = "요청하셨지만 AI가 하지 않았어요";

    /** 판정 2의 마지막 턴 전용 문장. 게이트에는 쓰지 않고 기록만 한다. */
    static final String LAST_TURN_OWNERSHIP = "이 턴이 마지막이라, AI가 정한 것을 확인하셨는지는 알 수 없어요";

    static final List<String> JUDGEMENTS = List.of(AS_ASKED, PARTIAL, NOT_IN_PROMPT, NOT_DONE);

    private static final String ORDER_SERVICE = "src/main/java/com/shop/OrderService.java";
    private static final String ORDER = "src/main/java/com/shop/Order.java";
    private static final String INVENTORY = "src/main/java/com/shop/Inventory.java";
    private static final String ORDER_CONTROLLER = "src/main/java/com/shop/OrderController.java";

    private LiveSessions() {
    }

    /**
     * @param withoutSignal 실행 데이터 없이 볼 때 맞는 판정 1. null이면 채점하지 않는 턴
     * @param withSignal    턴별 채점 결과를 실었을 때 맞는 판정 1
     */
    record Expected(String withoutSignal, String withSignal) {

        /** 신호가 판정을 뒤집는 턴. 게이트 2의 표적이다. */
        boolean decisive() {
            return withoutSignal != null && !withoutSignal.equals(withSignal);
        }

        static Expected same(String judgement) {
            return new Expected(judgement, judgement);
        }
    }

    record Session(String name, ProblemView problem, AttemptView attempt, List<Expected> turns) {
    }

    static List<Session> all() {
        return List.of(정석(), 범위초과(), 결정적턴(), 인프라사고(), 퇴보());
    }

    /**
     * S1 정석. 프롬프트가 매 턴 무엇을 원하는지 적었고 결과도 그대로 따라왔다.
     */
    private static Session 정석() {
        List<AttemptView.TurnView> turns = List.of(
                turn(
                        """
                        목표
                        - OrderService에 주문 취소 기능을 추가

                        작업 대상
                        - src/main/java/com/shop/OrderService.java, 지금은 취소 메서드가 아예 없음

                        요구사항
                        - cancel(orderId)는 주문 상태를 CANCELED로 바꿈

                        제약
                        - Order, OrderService, Inventory 밖의 파일은 만들지도 고치지도 말 것

                        완료 조건
                        - cancel을 부른 뒤 주문 상태가 CANCELED임

                        검증
                        - 고친 뒤 다시 확인하고, 실행할 수 없으면 그대로 알려줄 것""",
                        "OrderService에 cancel(Long orderId)를 추가하고 주문 상태를 CANCELED로 바꾸도록 했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_ONLY))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - cancel이 추가됐고 상태가 CANCELED로 바뀌는 것까지 확인했어요

                        요구사항
                        - 이미 배송이 시작된 주문(SHIPPED, DELIVERED)은 취소를 거부하고 IllegalStateException을 던짐

                        완료 조건
                        - SHIPPED 주문에 cancel을 부르면 IllegalStateException이 남""",
                        "cancel 앞에 배송 상태 검사를 넣고 SHIPPED와 DELIVERED에서 IllegalStateException을 던지게 했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - SHIPPED 주문이 IllegalStateException으로 막히는 것을 확인했어요

                        작업 대상
                        - Inventory에 재고를 되돌리는 메서드가 없음

                        요구사항
                        - 취소한 주문의 수량만큼 Inventory 재고를 되돌림""",
                        "Inventory에 restore를 추가하고 cancel이 취소 직후 수량만큼 재고를 되돌리게 했습니다.",
                        List.of(
                                modified(INVENTORY, inventoryWithRestore()),
                                modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD_AND_RESTORE))),
                        List.of(
                                new ToolCallEntry(ToolCallEntry.READ_FILE, INVENTORY),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, INVENTORY),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, ORDER_SERVICE))));

        return session("S1-정석", turns, List.of(
                Expected.same(AS_ASKED),
                Expected.same(AS_ASKED),
                Expected.same(AS_ASKED)));
    }

    /**
     * S2 범위 초과. 턴 2에서 AI가 아무도 요청하지 않은 OrderController를 새로 만들었고,
     * 턴 3 프롬프트에 그 파일 이름이 없다.
     */
    private static Session 범위초과() {
        List<AttemptView.TurnView> turns = List.of(
                turn(
                        """
                        목표
                        - 주문 취소를 만들어 줘

                        작업 대상
                        - OrderService

                        요구사항
                        - cancel(orderId)로 주문을 취소할 수 있음

                        제약
                        - (없음)

                        완료 조건
                        - 취소한 주문의 상태가 CANCELED

                        검증
                        - 고친 뒤 다시 봐 줘""",
                        "OrderService에 cancel(Long orderId)를 추가했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_ONLY))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        요구사항
                        - 배송이 시작된 주문은 취소를 막아 줘""",
                        "cancel에 배송 상태 검사를 넣었고, 취소 요청을 받을 자리가 없어 OrderController도 새로 만들었습니다.",
                        List.of(
                                modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD)),
                                added(ORDER_CONTROLLER, orderController())),
                        List.of(
                                new ToolCallEntry(ToolCallEntry.LIST_FILES, null),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, ORDER_SERVICE),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, ORDER_CONTROLLER))),
                turn(
                        """
                        직전 결과
                        - 배송이 시작된 주문이 막히는 것은 확인했어요

                        요구사항
                        - 취소한 수량만큼 Inventory 재고를 되돌림

                        제약
                        - Inventory와 OrderService만 고칠 것""",
                        "Inventory에 restore를 추가하고 cancel이 재고를 되돌리게 했습니다.",
                        List.of(
                                modified(INVENTORY, inventoryWithRestore()),
                                modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD_AND_RESTORE))),
                        List.of(
                                new ToolCallEntry(ToolCallEntry.READ_FILE, INVENTORY),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, INVENTORY),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, ORDER_SERVICE))));

        return session("S2-범위초과", turns, List.of(
                Expected.same(AS_ASKED),
                Expected.same(PARTIAL),
                Expected.same(AS_ASKED)));
    }

    /**
     * S3 결정적 턴. 턴 2·3은 요청한 기능의 변경이 파일에 분명히 있어 파일만 보면 통과처럼 읽히는데,
     * 그 요청을 가르는 테스트가 계속 실패로 남아 있다. 실행 데이터가 있어야만 갈리는 두 턴이다.
     */
    private static Session 결정적턴() {
        List<AttemptView.TurnView> turns = List.of(
                turn(
                        """
                        목표
                        - OrderService에 주문 취소를 추가

                        작업 대상
                        - OrderService에 취소 메서드가 없음

                        요구사항
                        - cancel(orderId)는 주문 상태를 CANCELED로 바꿈

                        제약
                        - Order, OrderService, Inventory 밖은 건드리지 말 것

                        완료 조건
                        - cancel 뒤 상태가 CANCELED

                        검증
                        - 고친 뒤 다시 확인해 줄 것""",
                        "OrderService에 cancel(Long orderId)를 추가했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_ONLY))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - cancel이 상태를 CANCELED로 바꾸는 것은 확인했어요

                        요구사항
                        - 배송이 시작된 주문은 취소를 거부하고 IllegalStateException을 던짐""",
                        "cancel 앞에 배송 상태 검사를 넣어 배송이 끝난 주문의 취소를 막았습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_DELIVERED_ONLY_GUARD))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - 배송 중인 주문을 막아 달라고 했는데 아직 그대로 취소돼요

                        요구사항
                        - SHIPPED 주문도 취소를 거부함""",
                        "SHIPPED도 검사 대상에 넣어 배송이 시작된 주문의 취소를 막았습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_WRONG_EXCEPTION))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - SHIPPED가 막히긴 하는데 던지는 예외 타입이 달라요

                        요구사항
                        - 배송이 시작된 주문의 취소는 IllegalStateException으로 거부함""",
                        "예외 타입을 IllegalStateException으로 바꿨습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD))),
                        readEdit(ORDER_SERVICE)));

        return session("S3-결정적턴", turns, List.of(
                Expected.same(AS_ASKED),
                new Expected(AS_ASKED, PARTIAL),
                new Expected(AS_ASKED, PARTIAL),
                Expected.same(AS_ASKED)));
    }

    /**
     * S4 인프라 사고. 턴 2는 채점 인프라가 죽어 통과 수를 알 수 없고, 턴 3은 실행 자체가 없다(솔로 모사).
     * 두 턴 모두 신호가 판정을 바꿔서는 안 된다 — 정보 없음은 정보가 아니다.
     */
    private static Session 인프라사고() {
        List<AttemptView.TurnView> turns = List.of(
                turn(
                        """
                        목표
                        - 주문 취소를 추가

                        작업 대상
                        - OrderService

                        요구사항
                        - cancel(orderId)는 상태를 CANCELED로 바꿈

                        제약
                        - 세 파일 밖은 건드리지 말 것

                        완료 조건
                        - cancel 뒤 상태가 CANCELED

                        검증
                        - 고친 뒤 확인해 줄 것""",
                        "OrderService에 cancel(Long orderId)를 추가했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_ONLY))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - cancel이 상태를 CANCELED로 바꾸는 것은 확인했어요

                        요구사항
                        - 배송이 시작된 주문은 IllegalStateException으로 취소를 거부함""",
                        "cancel 앞에 배송 상태 검사를 넣고 IllegalStateException을 던지게 했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - 배송이 시작된 주문이 막히는 것을 확인했어요

                        요구사항
                        - 취소한 수량만큼 Inventory 재고를 되돌림""",
                        "Inventory에 restore를 추가하고 cancel이 재고를 되돌리게 했습니다.",
                        List.of(
                                modified(INVENTORY, inventoryWithRestore()),
                                modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD_AND_RESTORE))),
                        List.of(
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, INVENTORY),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, ORDER_SERVICE))));

        return session("S4-인프라사고", turns, List.of(
                Expected.same(AS_ASKED),
                Expected.same(AS_ASKED),
                Expected.same(AS_ASKED)));
    }

    /**
     * S5 퇴보. 턴 3에서 요청한 재고 복구는 들어왔는데 앞 턴이 통과시킨 배송 검사가 깨져 델타가 음수다.
     * 마지막 턴은 판정 2의 마지막 턴 전용 문장도 함께 본다.
     */
    private static Session 퇴보() {
        List<AttemptView.TurnView> turns = List.of(
                turn(
                        """
                        목표
                        - 주문 취소를 추가

                        작업 대상
                        - OrderService에 취소가 없음

                        요구사항
                        - cancel(orderId)는 상태를 CANCELED로 바꿈

                        제약
                        - Order, OrderService, Inventory 밖은 건드리지 말 것

                        완료 조건
                        - cancel 뒤 상태가 CANCELED

                        검증
                        - 고친 뒤 확인해 줄 것""",
                        "OrderService에 cancel(Long orderId)를 추가했습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_ONLY))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - cancel이 상태를 CANCELED로 바꾸는 것을 확인했어요

                        요구사항
                        - 배송이 시작된 주문(SHIPPED, DELIVERED)은 IllegalStateException으로 취소를 거부함""",
                        "cancel 앞에 배송 상태 검사를 넣었습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD))),
                        readEdit(ORDER_SERVICE)),
                turn(
                        """
                        직전 결과
                        - 배송이 시작된 주문이 IllegalStateException으로 막히는 것을 확인했어요

                        요구사항
                        - 취소한 수량만큼 Inventory 재고를 되돌림""",
                        "Inventory에 restore를 추가하고, cancel을 재고 복구까지 하도록 다시 썼습니다.",
                        List.of(
                                modified(INVENTORY, inventoryWithRestore()),
                                modified(ORDER_SERVICE, orderService(RESTORE_WITHOUT_GUARD))),
                        List.of(
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, INVENTORY),
                                new ToolCallEntry(ToolCallEntry.EDIT_FILE, ORDER_SERVICE))),
                turn(
                        """
                        직전 결과
                        - 재고는 되돌아오는데 배송이 시작된 주문이 다시 취소돼요

                        요구사항
                        - 배송 상태 검사를 되살리고 재고 복구도 그대로 둠""",
                        "cancel에 배송 상태 검사를 되살리고 재고 복구는 그대로 두었습니다.",
                        List.of(modified(ORDER_SERVICE, orderService(CANCEL_WITH_GUARD_AND_RESTORE))),
                        readEdit(ORDER_SERVICE)));

        return session("S5-퇴보", turns, List.of(
                Expected.same(AS_ASKED),
                Expected.same(AS_ASKED),
                new Expected(AS_ASKED, PARTIAL),
                Expected.same(AS_ASKED)));
    }

    private static Session session(String name, List<AttemptView.TurnView> turns, List<Expected> expected) {
        ProblemView problem = new ProblemView(1L, "주문 취소와 재고 복구", SPEC_MD, skeleton());
        AttemptView attempt = AttemptView.reconstruct(
                1L, 1L, skeleton(), turns, AttemptStatus.IN_PROGRESS, null, null, null);

        return new Session(name, problem, attempt, expected);
    }

    private static AttemptView.TurnView turn(
            String userPrompt,
            String aiSummary,
            List<FileChange> changes,
            List<ToolCallEntry> toolCalls
    ) {
        return new AttemptView.TurnView(userPrompt, aiSummary, changes, toolCalls, null, null, null);
    }

    private static List<ToolCallEntry> readEdit(String path) {
        return List.of(
                new ToolCallEntry(ToolCallEntry.READ_FILE, path),
                new ToolCallEntry(ToolCallEntry.EDIT_FILE, path));
    }

    private static FileChange modified(String path, String content) {
        return new FileChange(path, FileChange.ChangeType.MODIFIED, content);
    }

    private static FileChange added(String path, String content) {
        return new FileChange(path, FileChange.ChangeType.ADDED, content);
    }

    private static List<ProblemFile> skeleton() {
        return List.of(
                new ProblemFile(ORDER, ORDER_SOURCE),
                new ProblemFile(ORDER_SERVICE, orderService(NO_CANCEL)),
                new ProblemFile(INVENTORY, INVENTORY_SOURCE));
    }

    private static final String SPEC_MD = """
            # 주문 취소와 재고 복구

            `OrderService`에 주문 취소를 추가한다.

            ## 요구사항
            - `cancel(orderId)`는 주문 상태를 `CANCELED`로 바꾼다.
            - 이미 배송이 시작된 주문(`SHIPPED`, `DELIVERED`)은 취소할 수 없고 `IllegalStateException`을 던진다.
            - 취소한 주문의 수량만큼 `Inventory`의 재고를 되돌린다.

            ## 제약
            - `Order`, `OrderService`, `Inventory` 밖의 파일은 만들지도 고치지도 않는다.
            """;

    private static final String ORDER_SOURCE = """
            package com.shop;

            public class Order {

                public enum Status { CREATED, PAID, SHIPPED, DELIVERED, CANCELED }

                private final Long id;
                private final String sku;
                private final int quantity;
                private Status status;

                public Order(Long id, String sku, int quantity, Status status) {
                    this.id = id;
                    this.sku = sku;
                    this.quantity = quantity;
                    this.status = status;
                }

                public Long id() {
                    return id;
                }

                public String sku() {
                    return sku;
                }

                public int quantity() {
                    return quantity;
                }

                public Status status() {
                    return status;
                }

                public void changeStatus(Status status) {
                    this.status = status;
                }
            }
            """;

    private static final String INVENTORY_SOURCE = """
            package com.shop;

            import java.util.HashMap;
            import java.util.Map;

            public class Inventory {

                private final Map<String, Integer> stock = new HashMap<>();

                public int quantityOf(String sku) {
                    return stock.getOrDefault(sku, 0);
                }

                public void reduce(String sku, int quantity) {
                    stock.put(sku, quantityOf(sku) - quantity);
                }
            }
            """;

    private static String inventoryWithRestore() {
        return """
                package com.shop;

                import java.util.HashMap;
                import java.util.Map;

                public class Inventory {

                    private final Map<String, Integer> stock = new HashMap<>();

                    public int quantityOf(String sku) {
                        return stock.getOrDefault(sku, 0);
                    }

                    public void reduce(String sku, int quantity) {
                        stock.put(sku, quantityOf(sku) - quantity);
                    }

                    public void restore(String sku, int quantity) {
                        stock.put(sku, quantityOf(sku) + quantity);
                    }
                }
                """;
    }

    private static String orderController() {
        return """
                package com.shop;

                public class OrderController {

                    private final OrderService orderService;

                    public OrderController(OrderService orderService) {
                        this.orderService = orderService;
                    }

                    public void cancel(Long orderId) {
                        orderService.cancel(orderId);
                    }
                }
                """;
    }

    private static final String NO_CANCEL = "";

    private static final String CANCEL_ONLY = """

                public void cancel(Long orderId) {
                    Order order = find(orderId);
                    order.changeStatus(Order.Status.CANCELED);
                }
            """;

    private static final String CANCEL_WITH_GUARD = """

                public void cancel(Long orderId) {
                    Order order = find(orderId);

                    if (order.status() == Order.Status.SHIPPED || order.status() == Order.Status.DELIVERED) {
                        throw new IllegalStateException("배송이 시작된 주문은 취소할 수 없습니다.");
                    }

                    order.changeStatus(Order.Status.CANCELED);
                }
            """;

    /** 요청은 "배송이 시작된 주문"인데 DELIVERED만 막았다 — SHIPPED는 그대로 취소된다. */
    private static final String CANCEL_WITH_DELIVERED_ONLY_GUARD = """

                public void cancel(Long orderId) {
                    Order order = find(orderId);

                    if (order.status() == Order.Status.DELIVERED) {
                        throw new IllegalStateException("배송이 끝난 주문은 취소할 수 없습니다.");
                    }

                    order.changeStatus(Order.Status.CANCELED);
                }
            """;

    /** SHIPPED도 막지만 던지는 예외 타입이 요구사항과 다르다. */
    private static final String CANCEL_WITH_WRONG_EXCEPTION = """

                public void cancel(Long orderId) {
                    Order order = find(orderId);

                    if (order.status() == Order.Status.SHIPPED || order.status() == Order.Status.DELIVERED) {
                        throw new IllegalArgumentException("배송이 시작된 주문은 취소할 수 없습니다.");
                    }

                    order.changeStatus(Order.Status.CANCELED);
                }
            """;

    private static final String CANCEL_WITH_GUARD_AND_RESTORE = """

                public void cancel(Long orderId) {
                    Order order = find(orderId);

                    if (order.status() == Order.Status.SHIPPED || order.status() == Order.Status.DELIVERED) {
                        throw new IllegalStateException("배송이 시작된 주문은 취소할 수 없습니다.");
                    }

                    order.changeStatus(Order.Status.CANCELED);
                    inventory.restore(order.sku(), order.quantity());
                }
            """;

    /** 재고 복구는 들어왔는데 앞 턴이 넣은 배송 상태 검사가 사라졌다. */
    private static final String RESTORE_WITHOUT_GUARD = """

                public void cancel(Long orderId) {
                    Order order = find(orderId);
                    order.changeStatus(Order.Status.CANCELED);
                    inventory.restore(order.sku(), order.quantity());
                }
            """;

    private static String orderService(String cancelMethod) {
        return """
                package com.shop;

                import java.util.Map;

                public class OrderService {

                    private final Map<Long, Order> orders;
                    private final Inventory inventory;

                    public OrderService(Map<Long, Order> orders, Inventory inventory) {
                        this.orders = orders;
                        this.inventory = inventory;
                    }

                    public Order find(Long orderId) {
                        Order order = orders.get(orderId);

                        if (order == null) {
                            throw new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId);
                        }

                        return order;
                    }
                """ + cancelMethod + "}\n";
    }
}
