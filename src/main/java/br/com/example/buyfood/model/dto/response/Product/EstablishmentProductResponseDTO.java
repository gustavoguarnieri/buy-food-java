package br.com.example.buyfood.model.dto.response.Product;

import br.com.example.buyfood.model.dto.response.ImageResponseDTO;
import br.com.example.buyfood.model.dto.response.IngredientResponseDTO;
import br.com.example.buyfood.model.dto.response.UploadFileResponseDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstablishmentProductResponseDTO {

    private Long id;
    private String name;
    private String price;
    private String description;
    private List<UploadFileResponseDTO> images;
    private EstablishmentResponseForProductDTO establishment;
    private List<IngredientResponseDTO> ingredients;
    private int status;
}