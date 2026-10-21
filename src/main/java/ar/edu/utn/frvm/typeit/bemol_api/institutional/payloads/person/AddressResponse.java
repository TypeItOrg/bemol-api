package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Address;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record AddressResponse(
    UUID id,
    String street,
    @Nullable String number,
    @Nullable String floor,
    @Nullable String apartment,
    @Nullable String neighborhood,
    @Nullable String additionalInfo,
    CitySummaryResponse city) {

  public static AddressResponse from(Address address) {
    return AddressResponse.builder()
        .id(address.getId())
        .street(address.getStreet())
        .number(address.getNumber())
        .floor(address.getFloor())
        .apartment(address.getApartment())
        .neighborhood(address.getNeighborhood())
        .additionalInfo(address.getAdditionalInfo())
        .city(
            CitySummaryResponse.builder()
                .id(address.getCity().getId())
                .name(address.getCity().getName())
                .provinceId(address.getCity().getProvince().getId())
                .province(address.getCity().getProvince().getName())
                .build())
        .build();
  }
}
