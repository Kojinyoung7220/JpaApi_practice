package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 조회
 * xToOne(ManyToOne, OneToOne)      투원 관계들 조회만 모아 놓았음!!
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository simpleQueryRepository; //v4에서 의존관계 주입

    /**
     * order member 와 order delivery 는 지연 로딩이다. 따라서 실제 엔티티 대신에 프록시 존재
     * jackson 라이브러리는 기본적으로 이 프록시 객체를 json으로 어떻게 생성해야 하는지 모름 예외 발생
     * Hibernate5Module 을 스프링 빈으로 등록하면 해결(스프링 부트 사용중) => 등록하게 되면 기본적으로 초기화 된 프록시 객체만 노출, 초기화 되지 않은 프록시 객체는 노출 안함. 강제로 지연 로딩 가능
     *
     * List<Order> 이렇게 엔티티를 직접 노출할때, 즉 양방향 연관관계가 걸린 곳은 @jsonIgnore으로 막아줘야 됨 안그러면 양쪽을 서로 호출하면서 무한 루프가 생김.
     * 하이버네이트모듈5
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() { //
        List<Order> allByString = orderRepository.findAllByString(new OrderSearch());   //order를 전부 끌고옴 => 2개
        for (Order order : allByString) {
            order.getMember().getName();        //order.getMember() 여기까지는 프록시를 가져와서 쿼리를 안날리는데 getName까지 하면 LAZY가 강제 초기화 되면서 가져옴 => 맴버안에 값들을 전부 가져옴
            order.getDelivery().getAddress();   //이하동문
        }
        return allByString;
    }

    /**
     * Hibernate5Module 를 사용하기 보다는 DTO로 변환해서 반환하는 것이 더 좋은 방법이다, 또한 엔티티를 API 응답으로 외부로 노출하는 것은 좋지 않다
     * v1에서 member를 전부 끌고오는 일이 발생함.
     * dto를 통해서 원하는 값들만 가져오게끔 만듬.
     *
     *  * V2. 엔티티를 조회해서 DTO로 변환(fetch join 사용X)
     *  * - 단점: 지연로딩으로 쿼리 N번 호출
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        //현재 order은 2개를 가져옴
        //N+1 -> 1 + N(2) => 1 + 회원 N(getMember().getName();) + 배송 N(getDelivery().getAddress()) => LAZE 2번 되니깐
        // 1번 쿼리를 날려서 얻은값 2개를 2번 루프도니깐 총 4번 쿼리를 날림 그래서 5개 ~~~~~~~~~~~~~
        //근데 만약 똑같은 맴버가 2번 주문했다고 치면 영속성 컨테스트에 있는 값을 그대로 쓰기 때문에
        // 1 + 1 + 2가 됨. 1 + 회원 + 배송이니깐.
        // 정리하면 1 + N 문제지만 N이 2개임 LAZY가 2번되니깐 회원과, 배송으로 그래서 1 + N + N 임 그러면 n이 2개이니깐(order조회 결과수) 1 + 2 + 2로 총 5번 쿼리를 날림
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());

        List<SimpleOrderDto> result = orders.stream()
                .map(order -> new SimpleOrderDto(order))
                .collect(Collectors.toList());

        return result;
    }
    /**
     * V3. 엔티티를 조회해서 DTO로 변환(fetch join 사용O)
     * - fetch join으로 쿼리 1번 호출
     * 참고: fetch join에 대한 자세한 내용은 JPA 기본편 참고(정말 중요함)
     * 총 쿼리 1번나감.
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(); // 패치조인으로 메서드 추가.
        List<SimpleOrderDto> result = orders.stream()   //order -> member , order -> delivery 는 이미 조회 된 상태 이므로 지연로딩X
                .map(o -> new SimpleOrderDto(o))        // 따라서 쿼리가 총 1번 나감. 테이블에 order 결과값이 2개 옆에 데이터가 쭈욱 붙기 때문에 그 데이터에서 조회하게됨.
                .collect(Collectors.toList());
        return result;
    }

    /**
     * V4. JPA에서 DTO로 바로 조회
     * - 쿼리 1번 호출
     * - select 절에서 원하는 데이터만 선택해서 조회
     *   private final OrderSimpleQueryRepository simpleQueryRepository; = > v4에서 의존관계 주입 코드 추가
     *
     *  v3에서는 엔티티를 조회하고 그걸 dto로 변환하는 과정을 거쳤지만 v4에서는 이런거 없이 바로 jpa에서 dto로 끄집어내줌.
     *
     *   일반적인 SQL을 사용할 때 처럼 원하는 값을 선택해서 조회
     *   new 명령어를 사용해서 JPQL의 결과를 DTO로 즉시 변환
     *   SELECT 절에서 원하는 데이터를 직접 선택하므로 => DB 애플리케이션 네트웍 용량 최적화(생각보다 미비), 그리고 select절에서 많이 퍼올림 네트워크 많이씀
     *   리포지토리 재사용성 떨어짐, API 스펙에 맞춘 코드가 리포지토리에 들어가는 단점
     *
     *   v3랑 v4랑 우열을 가리기 힘듬. 트레이드오프.
     */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return simpleQueryRepository.findOrderDto();
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName(); //LAZE 초기화
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress(); //LAZE 초기화
        }
    }
}