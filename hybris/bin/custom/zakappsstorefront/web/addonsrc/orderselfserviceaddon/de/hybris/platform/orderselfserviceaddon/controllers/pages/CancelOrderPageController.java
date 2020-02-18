/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2016 SAP SE or an SAP affiliate company.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package de.hybris.platform.orderselfserviceaddon.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.orderselfserviceaddon.facades.OrderCancelFacade;
import de.hybris.platform.orderselfserviceaddon.forms.OrderEntryCancelForm;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Controller for cancel order page
 */
@Controller
@RequestMapping(value = "/my-account/order")
public class CancelOrderPageController extends AbstractSearchPageController
{
	private static final Logger LOG = Logger.getLogger(CancelOrderPageController.class);
	private static final String BREADCRUMBS_ATTR = "breadcrumbs";
	private static final String REDIRECT_TO_ORDERS_HISTORY_PAGE = REDIRECT_PREFIX + "/my-account/orders";
	private static final String CANCEL_ORDER_CMS_PAGE = "cancel-order";
	private static final String CANCEL_CONFIRM_ORDER_CMS_PAGE = "confirm-cancel-order";

	@Resource(name = "orderFacade")
	private OrderFacade orderFacade;


	@Resource(name = "orderCancelFacade")
	private OrderCancelFacade orderCancelFacade;

	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	@Resource(name = "priceDataFactory")
	private PriceDataFactory priceDataFactory;

	@Resource(name = "commonI18NService")
	private CommonI18NService commonI18NService;

	/*
	 * Display the cancel order page
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/{orderCode:.*}/cancel", method = { RequestMethod.POST, RequestMethod.GET })
	public String showCancelOrderPage(@PathVariable(value = "orderCode") final String orderCode, final Model model,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException // NOSONAR
	{
		try
		{
			final OrderData orderDetails = orderFacade.getOrderDetailsForCode(orderCode);
			model.addAttribute("orderData", orderDetails);
			model.addAttribute("orderEntryCancelForm", initializeForm(orderDetails));

			final List<Breadcrumb> breadcrumbs = accountBreadcrumbBuilder.getBreadcrumbs(null);
			breadcrumbs.add(new Breadcrumb("/my-account/orders",
					getMessageSource().getMessage("text.account.orderHistory", null, getI18nService().getCurrentLocale()), null));
			breadcrumbs.add(new Breadcrumb("/my-account/order/" + orderCode, getMessageSource()
					.getMessage("text.account.order.orderBreadcrumb", new Object[] { orderDetails.getCode() }, "Order {0}",
							getI18nService().getCurrentLocale()), null));
			breadcrumbs.add(new Breadcrumb("#",
					getMessageSource().getMessage("text.account.cancelOrder", null, getI18nService().getCurrentLocale()), null));
			model.addAttribute(BREADCRUMBS_ATTR, breadcrumbs);

		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn("Attempted to load a order that does not exist or is not visible", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "system.error.page.not.found", null);
			return REDIRECT_TO_ORDERS_HISTORY_PAGE;
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(CANCEL_ORDER_CMS_PAGE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CANCEL_ORDER_CMS_PAGE));
		return getViewForPage(model);
	}


		/*
		 * Display the confirm cancel order page
		 */
	@RequireHardLogIn
	@RequestMapping(value = "/{orderCode:.*}/cancel/cancelconfirmation", method = RequestMethod.POST)
	public String confirmCancelOrderPage(@PathVariable("orderCode") final String orderCode,
			@ModelAttribute("orderEntryCancelForm") final OrderEntryCancelForm orderEntryCancelForm,
			final Model model, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		try
		{
			final OrderData orderDetails = orderFacade.getOrderDetailsForCode(orderCode);
			orderEntryCancelForm.getCancelEntryQuantity().forEach((entryNumber,qty) ->
			{
				orderDetails.getEntries().forEach(orderEntryData ->
				{
					// Case of MultiD product
					if(isMultidimensionalEntry(orderEntryData))
					{
						orderEntryData.getEntries().stream().filter(nestedOrderEntry -> nestedOrderEntry.getEntryNumber().equals(entryNumber))
								.forEach(nestedOrderEntryData -> setCancellablePrice(qty.longValue(),nestedOrderEntryData));
					}
					// Case of non MultiD product
					else
					{
						if (orderEntryData.getEntryNumber().equals(entryNumber))
						{
							setCancellablePrice(qty.longValue(),orderEntryData);
						}
					}
				});
			});
			model.addAttribute("orderData", orderDetails);
			model.addAttribute("orderEntryCancelForm", orderEntryCancelForm);
			final List<Breadcrumb> breadcrumbs = accountBreadcrumbBuilder.getBreadcrumbs(null);
			breadcrumbs.add(new Breadcrumb("/my-account/orders",
					getMessageSource().getMessage("text.account.orderHistory", null, getI18nService().getCurrentLocale()), null));
			breadcrumbs.add(new Breadcrumb("/my-account/order/" + orderCode, getMessageSource()
					.getMessage("text.account.order.orderBreadcrumb", new Object[] { orderDetails.getCode() }, "Order {0}",
							getI18nService().getCurrentLocale()), null));
			breadcrumbs.add(new Breadcrumb("/my-account/order/" + orderCode + "/cancel",
					getMessageSource().getMessage("text.account.cancelOrder", null, getI18nService().getCurrentLocale()), null));
			breadcrumbs.add(new Breadcrumb("#",
					getMessageSource().getMessage("text.account.confirm.cancelOrder", null, getI18nService().getCurrentLocale()),
					null));
			model.addAttribute(BREADCRUMBS_ATTR, breadcrumbs);

		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn("Attempted to load a order that does not exist or is not visible", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "system.error.page.not.found", null);
			return REDIRECT_TO_ORDERS_HISTORY_PAGE;
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(CANCEL_CONFIRM_ORDER_CMS_PAGE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CANCEL_CONFIRM_ORDER_CMS_PAGE));
		return getViewForPage(model);
	}

	/**
	 * Confirms if the given {@link OrderEntryData} is for multidimensional product
	 *
	 * @param orderEntry
	 * 		the given {@link OrderEntryData}
	 * @return true, if the given {@link OrderEntryData} is for multidimensional product
	 */
	protected boolean isMultidimensionalEntry(final OrderEntryData orderEntry)
	{
		return orderEntry.getProduct().getMultidimensional() != null && orderEntry.getProduct().getMultidimensional() && !orderEntry
				.getEntries().isEmpty();
	}

	/**
	 * Updates the {@link OrderEntryData#cancelledItemsPrice} for the given requested cancel quantity
	 *
	 * @param qty
	 * 		the quantity to be cancelled from the given {@link OrderEntryData}
	 * @param orderEntryData
	 * 		the {@link OrderEntryData}
	 */
	protected void setCancellablePrice(final Long qty, final OrderEntryData orderEntryData)
	{
		final PriceData cancelledItemsPriceData = priceDataFactory
				.create(PriceDataType.BUY, orderEntryData.getBasePrice().getValue().multiply(new BigDecimal(qty)),
						commonI18NService.getCurrentCurrency());
		orderEntryData.setCancelledItemsPrice(cancelledItemsPriceData);
	}



	/*
		 * submit the confirmed cancel items to be cancelled
		 */
	@RequireHardLogIn
	@RequestMapping(value = "/{orderCode:.*}/cancel/submit", method = RequestMethod.POST)
	public String submitCancelOrderPage(@PathVariable("orderCode") final String orderCode,
			@ModelAttribute("orderEntryCancelForm") final OrderEntryCancelForm orderEntryCancelForm,
			final Model model, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		try
		{
			orderCancelFacade
					.requestOrderCancel(orderCode, orderEntryCancelForm.getCancelEntryQuantity());
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.INFO_MESSAGES_HOLDER, getMessageSource()
					.getMessage("text.account.cancel.success", null, getI18nService().getCurrentLocale()), null);
			return REDIRECT_TO_ORDERS_HISTORY_PAGE;
		}
		catch (Exception exception)
		{
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "text.account.cancel.fail.generic");
			return REDIRECT_TO_ORDERS_HISTORY_PAGE;
		}
	}


	protected OrderEntryCancelForm initializeForm(OrderData orderData)
	{
		OrderEntryCancelForm orderEntryCancelForm = new OrderEntryCancelForm();
		Map<Integer, Integer> cancelEntryQuantity = new HashMap<>();
		if (CollectionUtils.isNotEmpty(orderData.getEntries()))
		{
			for (OrderEntryData orderEntryData : orderData.getEntries())
			{
				if (orderEntryData.getProduct().getMultidimensional() != null && orderEntryData.getProduct().getMultidimensional())
				{
					for (OrderEntryData nestedOrderEntryData : orderEntryData.getEntries())
					{
						cancelEntryQuantity.put(nestedOrderEntryData.getEntryNumber(), 0);
					}
				}
				else
				{
					cancelEntryQuantity.put(orderEntryData.getEntryNumber(), 0);
				}
			}
		}
		orderEntryCancelForm.setCancelEntryQuantity(cancelEntryQuantity);
		return orderEntryCancelForm;

	}


}