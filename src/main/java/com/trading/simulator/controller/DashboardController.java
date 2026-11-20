package com.trading.simulator.controller;

import com.trading.simulator.dto.OrderRequest;
import com.trading.simulator.dto.OrderResponse;
import com.trading.simulator.dto.SubmitResponse;
import com.trading.simulator.engine.OrderNotCancellableException;
import com.trading.simulator.engine.OrderNotFoundException;
import com.trading.simulator.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The browser dashboard (Thymeleaf + Bootstrap). */
@Controller
public class DashboardController {

    private final TradingService service;

    public DashboardController(TradingService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", service.getStats());
        model.addAttribute("books", service.getAllOrderBooks());
        model.addAttribute("openOrders", service.listOrders(null, null, null).stream()
                .filter(order -> order.status().name().equals("OPEN")
                        || order.status().name().equals("PARTIALLY_FILLED"))
                .toList());
        model.addAttribute("trades", service.listTrades(null, 20));
        return "index";
    }

    @PostMapping("/orders")
    public String placeOrder(@Valid @ModelAttribute OrderRequest orderForm,
                             BindingResult bindingResult,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/";
        }
        try {
            SubmitResponse response = service.placeOrder(orderForm);
            OrderResponse order = response.order();
            redirect.addFlashAttribute("message", "Order #" + order.id() + " " + order.status()
                    + (response.trades().isEmpty() ? "" : " (" + response.trades().size() + " trade(s) executed)"));
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable long id, RedirectAttributes redirect) {
        try {
            service.cancelOrder(id);
            redirect.addFlashAttribute("message", "Order #" + id + " cancelled");
        } catch (OrderNotFoundException | OrderNotCancellableException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }
}
