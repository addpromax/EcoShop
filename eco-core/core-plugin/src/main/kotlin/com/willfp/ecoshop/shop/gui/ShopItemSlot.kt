package com.willfp.ecoshop.shop.gui

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.onLeftClick
import com.willfp.eco.core.gui.onRightClick
import com.willfp.eco.core.gui.onShiftLeftClick
import com.willfp.eco.core.gui.onShiftRightClick
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.CustomSlot
import com.willfp.eco.core.items.builder.modify
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.toNiceString
import com.willfp.ecoshop.shop.BuyStatus
import com.willfp.ecoshop.shop.BuyType
import com.willfp.ecoshop.shop.SellStatus
import com.willfp.ecoshop.shop.ShopItem
import com.willfp.ecoshop.shop.configKey
import com.willfp.ecoshop.shop.getDisplay
import com.willfp.ecoshop.shop.parentShop
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun Collection<String>.replaceIn(token: String, replacement: Any?) =
    this.map { it.replace(token, replacement.toNiceString()) }

class ShopItemSlot(
    item: ShopItem,
    plugin: EcoPlugin
) : CustomSlot() {
    private val slot = slot(extractItemStack(item, plugin)) {
        fun handleMainBuyClick(player: Player, menu: Menu, buyType: BuyType) {
            val status = item.getBuyStatus(player, 1, buyType)

            if (status != BuyStatus.ALLOW) {
                player.sendMessage(
                    plugin.langYml.getMessage("buy-status.${status.configKey}")
                        .replace("%price%", item.getBuyPrice(buyType).getDisplay(player, 1))
                )
            } else {
                if (item.getMaxBuysAtOnce(player) == 1) {
                    item.buy(
                        player,
                        1,
                        buyType,
                        shop = menu.parentShop[player]
                    )

                    player.sendMessage(
                        plugin.langYml.getMessage("bought-item")
                            .replace("%item%", item.displayName)
                            .replace("%price%", item.getBuyPrice(buyType)?.getDisplay(player) ?: "")
                    )
                } else {
                    menu.parentShop[player]?.clickSound?.playTo(player)
                    item.getBuyMenu(buyType).open(player, menu)
                }
            }
        }

        fun handleQuickBuyClick(player: Player, menu: Menu, buyType: BuyType) {
            val status = item.getBuyStatus(player, item.buyAmount, buyType)

            if (status != BuyStatus.ALLOW) {
                player.sendMessage(
                    plugin.langYml.getMessage("buy-status.${status.configKey}")
                        .replace("%price%", item.getBuyPrice(buyType).getDisplay(player, item.buyAmount))
                )
            } else {
                item.buy(
                    player,
                    item.buyAmount,
                    buyType,
                    shop = menu.parentShop[player]
                )

                player.sendMessage(
                    plugin.langYml.getMessage("bought-item-multiple")
                        .replace("%amount%", item.buyAmount.toString())
                        .replace("%item%", item.displayName)
                        .replace("%price%", item.getBuyPrice(buyType).getDisplay(player, item.buyAmount))
                )
            }
        }

        onLeftClick { player, _, _, menu -> handleMainBuyClick(player, menu, BuyType.NORMAL) }

        onShiftLeftClick { player, _, _, menu -> handleQuickBuyClick(player, menu, BuyType.NORMAL) }

        onRightClick { player, _, _, menu ->
            if (item.hasAltBuy) {
                handleMainBuyClick(player, menu, BuyType.ALT)
            } else {
                // Is selling
                val status = item.getCurrentSellStatus(player)

                if (status != SellStatus.ALLOW) {
                    player.sendMessage(
                        plugin.langYml.getMessage("sell-status.${status.configKey}")
                    )
                } else {
                    menu.parentShop[player]?.clickSound?.playTo(player)
                    item.sellMenu.open(player, menu)
                }
            }
        }

        onShiftRightClick { player, _, _, menu ->
            if (item.hasAltBuy) {
                handleQuickBuyClick(player, menu, BuyType.ALT)
            } else {
                // Is Selling
                val status = item.getCurrentSellStatus(player)

                if (status != SellStatus.ALLOW) {
                    player.sendMessage(
                        plugin.langYml.getMessage("sell-status.${status.configKey}")
                    )
                } else {
                    val amount = item.getAmountInPlayerInventory(player)

                    item.sell(player, amount)
                    player.sendMessage(
                        plugin.langYml.getMessage("sold-item")
                            .replace("%amount%", amount.toString())
                            .replace("%item%", item.displayName)
                            .replace("%price%", item.sellPrice.getDisplay(player, amount))
                    )
                }
            }
        }
    }

    private fun extractItemStack(item: ShopItem, plugin: EcoPlugin): (Player, Menu) -> ItemStack {
        return { player: Player, _: Menu ->
            val playerBuysLeft = item.getBuysLeft(player)
            val zeroBuysLeftMessage = plugin.langYml.getStrings("cant-buy-again")
            val fallbackPriceMessage = if (playerBuysLeft == 1) {
                "one-buy-price"
            } else {
                "buy-price"
            }
            val altBuyMessage = plugin.langYml.getStrings("dual-buy-price")
                .replaceIn("%price%", item.buyPrice?.getDisplay(player) ?: "")
                .replaceIn("%alt_price%", item.altBuyPrice?.getDisplay(player) ?: "")
            val status = item.getBuyStatus(player, 1, BuyType.NORMAL)
            val itemStack = item.displayItem.modify {
                if (item.isBuyable) {
                    addLoreLines(
                        if (status == BuyStatus.MISSING_REQUIREMENTS || status == BuyStatus.NO_PERMISSION) {
                            plugin.langYml.getStrings("lore.${status.configKey}")
                        } else {
                            if (playerBuysLeft == 0) {
                                zeroBuysLeftMessage
                            } else {
                                if (item.hasAltBuy) {
                                    altBuyMessage
                                } else {
                                    plugin.langYml.getStrings(fallbackPriceMessage)
                                        .replaceIn("%price%", item.buyPrice?.getDisplay(player) ?: "")
                                }
                            }
                        }
                    )
                }

                if (item.isSellable) {
                    addLoreLines(
                        plugin.langYml.getStrings("sell-price")
                            .replaceIn("%price%", item.sellPrice?.getDisplay(player) ?: "")
                    )
                }

                if (item.isShowingQuickBuySell) {
                    if (item.isBuyable && item.getMaxBuysAtOnce(player) > item.buyAmount) {
                        if (item.hasAltBuy) {
                            addLoreLines(
                                plugin.langYml.getStrings("quick-buy-alt-present")
                                    .replaceIn("%price%", item.buyPrice.getDisplay(player, item.buyAmount))
                                    .replaceIn("%amount%", item.buyAmount.toString())
                            )
                            addLoreLines(
                                plugin.langYml.getStrings("quick-buy-alt")
                                    .replaceIn("%price%", item.altBuyPrice.getDisplay(player, item.buyAmount))
                                    .replaceIn("%amount%", item.buyAmount.toString())
                            )
                        } else {
                            addLoreLines(
                                plugin.langYml.getStrings("quick-buy")
                                    .replaceIn("%amount%", item.buyAmount.toString())
                            )
                        }
                    }

                    if (item.isSellable) {
                        addLoreLines(
                            plugin.langYml.getStrings("quick-sell")
                        )

                        addLoreLines(
                            plugin.configYml.getStrings("shop-items.global-bottom-lore.sell").formatEco(player)
                        )
                    }
                }

                addLoreLines(
                    item.bottomLore.formatEco(player)
                )

                if (item.isBuyable) {
                    addLoreLines(plugin.configYml.getStrings("shop-items.global-bottom-lore.buy").formatEco(player))
                }

                if (item.isSellable) {
                    addLoreLines(plugin.configYml.getStrings("shop-items.global-bottom-lore.sell").formatEco(player))
                }

                addLoreLines(plugin.configYml.getStrings("shop-items.global-bottom-lore.always").formatEco(player))
            }

            val meta = itemStack.itemMeta
            val lore = (meta?.lore ?: emptyList())
                .replaceIn("%playerbuys%", item.getTotalBuys(player))
                .replaceIn("%playerlimit%", item.limit)
                .replaceIn("%globalbuys%", item.getTotalGlobalBuys())
                .replaceIn("%globallimit%", item.globalLimit)
            meta?.lore = lore
            itemStack.itemMeta = meta
            itemStack
        }
    }

    init {
        init(slot)
    }

}
