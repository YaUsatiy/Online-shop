package by.web.shop.handler;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import by.web.shop.model.CheckoutModel;
import by.web.shop.model.UserModel;
import by.web.shop.shopbackend.dao.CartLineDao;
import by.web.shop.shopbackend.dao.ProductDao;
import by.web.shop.shopbackend.dao.UserDao;
import by.web.shop.shopbackend.dto.Address;
import by.web.shop.shopbackend.dto.Cart;
import by.web.shop.shopbackend.dto.CartLine;
import by.web.shop.shopbackend.dto.OrderDetail;
import by.web.shop.shopbackend.dto.OrderItem;
import by.web.shop.shopbackend.dto.Product;
import by.web.shop.shopbackend.dto.User;

@Component
public class CheckoutHandler {
	@Autowired
	private UserDao userDao;
	@Autowired
	private ProductDao productDao;
	@Autowired
	private CartLineDao cartLineDao;
	@Autowired
	private HttpSession session;
	
	public CheckoutModel init(String name) throws Exception{

		User user = userDao.getByEmail(name);
		CheckoutModel checkoutModel = null;	

		if(user!=null) {
			checkoutModel = new CheckoutModel();
			checkoutModel.setUser(user);
			checkoutModel.setCart(user.getCart());
			
			double checkoutTotal = 0.0;
			List<CartLine> cartLines = cartLineDao.listAvailable(user.getCart().getId());

			if(cartLines.size() == 0 ) {
				throw new Exception("There are no products available for checkout now!");
			}
			
			for(CartLine cartLine: cartLines) {
				checkoutTotal += cartLine.getTotal();
			}
			
			checkoutModel.setCartLines(cartLines);
			checkoutModel.setCheckoutTotal(checkoutTotal);			
		}			
		
		return checkoutModel;
	}
	
	
	public List<Address> getShippingAddresses(CheckoutModel model) {
				
		List<Address> addresses = userDao.listShippingAddresses(model.getUser().getId());
		
		if(addresses.size() == 0) {
			addresses = new ArrayList<>();
		}

		addresses.add(addresses.size(), userDao.getBillingAddress(model.getUser().getId()));
		return addresses;
		
	}
	
	public String saveAddressSelection(CheckoutModel checkoutModel, int shippingId) {

		String transitionValue = "success";
		
		//logger.info(String.valueOf(shippingId));
		
		Address shipping = userDao.getAddress(shippingId);		
		
		checkoutModel.setShipping(shipping);
		
		return transitionValue;
		
	}
			
	
	public String saveAddress(CheckoutModel checkoutModel, Address shipping) {

		String transitionValue = "success";
		
		// set the user id
		// set shipping as true
		shipping.setUserId(checkoutModel.getUser().getId());
		shipping.setShipping(true);
		userDao.addAddress(shipping);
		
		// set the shipping id to flowScope object
		checkoutModel.setShipping(shipping);
		
		return transitionValue;
		
	}
		

	public String saveOrder(CheckoutModel checkoutModel) {
		String transitionValue = "success";	
		
		// create a new order object
		OrderDetail orderDetail = new OrderDetail();
				
		// attach the user 
		orderDetail.setUser(checkoutModel.getUser());
				
		// attach the shipping address
		orderDetail.setShipping(checkoutModel.getShipping());
		
		// fetch the billing address
		Address billing = userDao.getBillingAddress(checkoutModel.getUser().getId());		
		orderDetail.setBilling(billing);

		List<CartLine> cartLines = checkoutModel.getCartLines();
		OrderItem orderItem = null;
		
		double orderTotal = 0.0;
		int orderCount = 0;
		Product product = null;
		
		for(CartLine cartLine : cartLines) {
			
			orderItem = new OrderItem();
			
			orderItem.setBuyingPrice(cartLine.getBuyingPrice());
			orderItem.setProduct(cartLine.getProduct());
			orderItem.setProductCount(cartLine.getProductCount());
			orderItem.setTotal(cartLine.getTotal());
			
			orderItem.setOrderDetail(orderDetail);
			orderDetail.getOrderItems().add(orderItem);
			
			orderTotal += orderItem.getTotal();
			orderCount++;
			
			// update the product
			// reduce the quantity of product
			product = cartLine.getProduct();
			product.setQuantity(product.getQuantity() - cartLine.getProductCount());
			product.setPurchases(product.getPurchases() + cartLine.getProductCount());
			productDao.update(product);
			
			// delete the cartLine
			cartLineDao.remove(cartLine);			
		}
		
		orderDetail.setOrderTotal(orderTotal);
		orderDetail.setOrderCount(orderCount);
		orderDetail.setOrderDate(new Date());
		
		// save the order
		cartLineDao.addOrderDetail(orderDetail);

		// set it to the orderDetails of checkoutModel
		checkoutModel.setOrderDetail(orderDetail);

		
		// update the cart
		Cart cart = checkoutModel.getCart();
		cart.setGrandTotal(cart.getGrandTotal() - orderTotal);
		cart.setCartLines(cart.getCartLines() - orderCount);
		cartLineDao.updateCart(cart);
		
		// update the cart if its in the session
		UserModel userModel = (UserModel) session.getAttribute("userModel");
		if(userModel!=null) {
			userModel.setCart(cart);
		}
		
		return transitionValue;		
	}

	
	public OrderDetail getOrderDetail(CheckoutModel checkoutModel) {
		return checkoutModel.getOrderDetail();
	}
}