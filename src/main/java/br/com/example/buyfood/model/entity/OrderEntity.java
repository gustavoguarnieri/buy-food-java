package br.com.example.buyfood.model.entity;

import br.com.example.buyfood.enums.PaymentStatus;
import br.com.example.buyfood.enums.RegisterStatus;
import br.com.example.buyfood.model.embeddable.Audit;
import java.io.Serializable;
import java.util.List;
import javax.persistence.CascadeType;
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
import lombok.Getter;
import lombok.Setter;

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
  @JoinColumn(name = "delivery_address_id", referencedColumnName = "id")
  private DeliveryAddressEntity deliveryAddress;

  @ManyToOne
  @JoinColumn(name = "establishment_id", referencedColumnName = "id")
  private EstablishmentEntity establishment;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
  private List<OrderItemsEntity> items;

  @ManyToOne private PaymentWayEntity paymentWay;

  @NotBlank
  @Column(nullable = false)
  private String paymentStatus = PaymentStatus.PENDING.name();

  @ManyToOne private PreparationStatusEntity preparationStatus;

  private String observation;

  @Column(nullable = false)
  private int status = RegisterStatus.ENABLED.getValue();

  @Embedded private Audit audit = new Audit();
}
