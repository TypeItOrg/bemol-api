package ar.edu.utn.frvm.typeit.bemol_api;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/greeting")
@RestController
public class GreetingController {
  @GetMapping(version = Version.V1)
  public String greeting(@RequestParam(defaultValue = "John Doe") String name) {
    return String.format("Hello, %s!", name);
  }
}
