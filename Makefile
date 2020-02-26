.phony: start
start:
	CYC_SERVER= CYC_PORT= ZULIP_USERNAME= ZULIP_APIKEY= ZULIP_BASEURL= clj -m dendrite.core
