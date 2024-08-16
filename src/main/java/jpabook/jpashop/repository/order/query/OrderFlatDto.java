package jpabook.jpashop.repository.order.query;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 쿼리 한방으로 모든 걸 가져오기 위한 DTO
 * 오더랑 오더 아이템, 오더 아이템이랑 , 아이템을 조인한다
 * 오더와 오더 아이템은 oneMany 관계여서 중복(row수가 늘어남)으로 데이터가 들어감
 * 오더 아이템과 아이템은 toOne 관계이기 때문에 그냥 옆으로 데이터가 들어감
 * dto는 페치조인이 안되고 join으로만 가능하기 때문에
 *
 */
@Data
public class OrderFlatDto {

    private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;

    private String itemName;
    private int orderPrice;
    private int count;

    public OrderFlatDto(Long orderId, String name, LocalDateTime orderDate, OrderStatus orderStatus, Address address, String itemName, int orderPrice, int count) {
        this.orderId = orderId;
        this.name = name;
        this.orderDate = orderDate;
        this.orderStatus = orderStatus;
        this.address = address;
        this.itemName = itemName;
        this.orderPrice = orderPrice;
        this.count = count;
    }
}
