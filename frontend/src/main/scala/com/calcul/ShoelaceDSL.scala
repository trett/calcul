package com.calcul

import com.raquo.laminar.tags.HtmlTag
import com.raquo.laminar.keys.HtmlAttr
import com.raquo.laminar.codecs.*
import org.scalajs.dom

object ShoelaceDSL:

  // Custom Shoelace HTML Elements
  val slButton: HtmlTag[dom.HTMLElement]           = new HtmlTag[dom.HTMLElement]("sl-button", void = false)
  val slButtonGroup: HtmlTag[dom.HTMLElement]      = new HtmlTag[dom.HTMLElement]("sl-button-group", void = false)
  val slCard: HtmlTag[dom.HTMLElement]             = new HtmlTag[dom.HTMLElement]("sl-card", void = false)
  val slDialog: HtmlTag[dom.HTMLElement]           = new HtmlTag[dom.HTMLElement]("sl-dialog", void = false)
  val slInput: HtmlTag[dom.HTMLInputElement]       = new HtmlTag[dom.HTMLInputElement]("sl-input", void = false)
  val slTextarea: HtmlTag[dom.HTMLTextAreaElement] = new HtmlTag[dom.HTMLTextAreaElement]("sl-textarea", void = false)
  val slProgressBar: HtmlTag[dom.HTMLElement]      = new HtmlTag[dom.HTMLElement]("sl-progress-bar", void = false)
  val slProgressRing: HtmlTag[dom.HTMLElement]     = new HtmlTag[dom.HTMLElement]("sl-progress-ring", void = false)
  val slTag: HtmlTag[dom.HTMLElement]              = new HtmlTag[dom.HTMLElement]("sl-tag", void = false)
  val slBadge: HtmlTag[dom.HTMLElement]            = new HtmlTag[dom.HTMLElement]("sl-badge", void = false)
  val slIcon: HtmlTag[dom.HTMLElement]             = new HtmlTag[dom.HTMLElement]("sl-icon", void = false)
  val slAvatar: HtmlTag[dom.HTMLElement]           = new HtmlTag[dom.HTMLElement]("sl-avatar", void = false)
  val slSpinner: HtmlTag[dom.HTMLElement]          = new HtmlTag[dom.HTMLElement]("sl-spinner", void = false)
  val slDivider: HtmlTag[dom.HTMLElement]          = new HtmlTag[dom.HTMLElement]("sl-divider", void = false)
  val slAlert: HtmlTag[dom.HTMLElement]            = new HtmlTag[dom.HTMLElement]("sl-alert", void = false)

  // Common Shoelace Attributes
  val slVariant: HtmlAttr[String]     = new HtmlAttr[String]("variant", StringAsIsCodec)
  val slSize: HtmlAttr[String]        = new HtmlAttr[String]("size", StringAsIsCodec)
  val slName: HtmlAttr[String]        = new HtmlAttr[String]("name", StringAsIsCodec)
  val slLabel: HtmlAttr[String]       = new HtmlAttr[String]("label", StringAsIsCodec)
  val slPlaceholder: HtmlAttr[String] = new HtmlAttr[String]("placeholder", StringAsIsCodec)
  val slHelpText: HtmlAttr[String]    = new HtmlAttr[String]("help-text", StringAsIsCodec)
  val slImage: HtmlAttr[String]       = new HtmlAttr[String]("image", StringAsIsCodec)
  val slInitials: HtmlAttr[String]    = new HtmlAttr[String]("initials", StringAsIsCodec)
  val slOpen: HtmlAttr[Boolean]       = new HtmlAttr[Boolean]("open", BooleanAsAttrPresenceCodec)
  val slLoading: HtmlAttr[Boolean]    = new HtmlAttr[Boolean]("loading", BooleanAsAttrPresenceCodec)
  val slDisabled: HtmlAttr[Boolean]   = new HtmlAttr[Boolean]("disabled", BooleanAsAttrPresenceCodec)
  val slClearable: HtmlAttr[Boolean]  = new HtmlAttr[Boolean]("clearable", BooleanAsAttrPresenceCodec)
  val slSlot: HtmlAttr[String]        = new HtmlAttr[String]("slot", StringAsIsCodec)
  val slPill: HtmlAttr[Boolean]       = new HtmlAttr[Boolean]("pill", BooleanAsAttrPresenceCodec)
  val slOutline: HtmlAttr[Boolean]    = new HtmlAttr[Boolean]("outline", BooleanAsAttrPresenceCodec)
  val slValue: HtmlAttr[String]       = new HtmlAttr[String]("value", StringAsIsCodec)
  val slValueInt: HtmlAttr[Int]       = new HtmlAttr[Int]("value", IntAsStringCodec)
  val slType: HtmlAttr[String]        = new HtmlAttr[String]("type", StringAsIsCodec)
  val slPasswordToggle: HtmlAttr[Boolean] =
    new HtmlAttr[Boolean]("password-toggle", BooleanAsAttrPresenceCodec)

  // Common Shoelace Event Props
  val onSlRequestClose: com.raquo.laminar.keys.EventProp[dom.Event] =
    new com.raquo.laminar.keys.EventProp[dom.Event]("sl-request-close")
