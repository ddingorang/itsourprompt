package com.promptstudio.buildandtest;

import java.util.List;

/**
 * 격리 방식의 교체 지점.
 *
 * <p>현재 구현({@link ProcessCodeExecutor})은 워커 컨테이너 안에서 자식 프로세스로 실행한다.
 * 실행마다 일회용 컨테이너를 띄우는 구현으로 갈아끼울 때 이 인터페이스만 다시 구현하면 되고,
 * 백엔드 코드나 메시지 계약은 건드리지 않는다.
 */
public interface CodeExecutor {

    RunOutcome execute(List<RunRequestMessage.RunFileMessage> files);
}
