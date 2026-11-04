package com.flety.transporte;

import com.flety.commons.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
@ActiveProfiles("test")
class TransporteServiceApplicationTests {

	@Autowired // Para acordarme cuando me pregunten porque soy un wachin: esta etiqueta permite inyectar dependencias en el contexto de Spring, es decir que permite que Spring cree e inyecte automáticamente una instancia de la clase ApplicationContext en la variable contexto. (inyectar es el proceso de proporcionar una instancia de una clase a otra clase que la necesita)
	ApplicationContext contexto;

	@Test
	void elContextoLevantaConH2YLaLibreriaComun() {
		assertThat(contexto.getBean(GlobalExceptionHandler.class)).isNotNull();
	}

}
