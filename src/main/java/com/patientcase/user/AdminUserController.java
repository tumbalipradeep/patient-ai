package com.patientcase.user;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String newUserForm(Model model) {
        model.addAttribute("userForm", new UserCreateRequest());
        model.addAttribute("roles", Role.values());
        return "admin/users/form";
    }

    @PostMapping("/new")
    public String createUser(@Valid @ModelAttribute("userForm") UserCreateRequest request,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "admin/users/form";
        }
        try {
            userService.createUser(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "User '" + request.getUsername() + "' created successfully.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("roles", Role.values());
            return "admin/users/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        UserEditRequest req = new UserEditRequest();
        req.setEmail(user.getEmail());
        req.setFirstName(user.getFirstName());
        req.setLastName(user.getLastName());
        req.setRole(user.getRole());
        model.addAttribute("userForm", req);
        model.addAttribute("user", user);
        model.addAttribute("roles", Role.values());
        return "admin/users/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateUser(@PathVariable Long id,
                              @Valid @ModelAttribute("userForm") UserEditRequest request,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("user", userService.findById(id));
            model.addAttribute("roles", Role.values());
            return "admin/users/edit";
        }
        try {
            userService.updateUser(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "User updated successfully.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("user", userService.findById(id));
            model.addAttribute("roles", Role.values());
            return "admin/users/edit";
        }
    }

    @PostMapping("/{id}/enable")
    public String enableUser(@PathVariable Long id, Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            userService.setEnabled(id, true, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "User account enabled.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/disable")
    public String disableUser(@PathVariable Long id, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            userService.setEnabled(id, false, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "User account disabled.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
