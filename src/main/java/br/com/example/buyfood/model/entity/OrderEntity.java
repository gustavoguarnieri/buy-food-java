package br.com.example.buyfood.model.entity;

import br.com.example.buyfood.enums.PaymentStatus;
import br.com.example.buyfood.enums.RegisterStatus;
import br.com.example.buyfood.model.embeddable.Audit;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "orders")
public class OrderEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "keycloack_user_id", referencedColumnName = "userId")
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "delivery_address_id", referencedColumnName = "id")
    private DeliveryAddressEntity deliveryAddress;

    @ManyToOne
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @OneToMany(mappedBy = "id")
    private List<OrderItemsEntity> items;

    @NotBlank
    @Column(nullable = false)
    private String paymentWay;

    @NotBlank
    @Column(nullable = false)
    private String paymentStatus = PaymentStatus.PENDING.name();

    private String observation;

    @Column(nullable = false)
    private int status = RegisterStatus.ENABLED.getValue();

    @Embedded
    private Audit audit = new Audit();

    public BigDecimal getValorTotal() {
        return items.stream().map(OrderItemsEntity::getSubTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}